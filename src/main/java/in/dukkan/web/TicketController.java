package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.domain.SupportTicket;
import in.dukkan.domain.TicketKind;
import in.dukkan.domain.TicketMessage;
import in.dukkan.domain.TicketStatus;
import in.dukkan.repository.ShopRepository;
import in.dukkan.repository.TicketRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    public record CreateTicketRequest(
            TicketKind kind,
            @NotBlank String subject,
            String shopId,
            String orderId,
            String listingId,
            String body,
            List<String> imageUrls) {}

    public record TicketMessageRequest(String body, List<String> imageUrls) {}

    public record TicketPatch(TicketStatus status, String assignedToUserId, Boolean hidden) {}

    private final TicketRepository tickets;
    private final ShopRepository shops;
    private final Access access;

    public TicketController(TicketRepository tickets, ShopRepository shops, Access access) {
        this.tickets = tickets;
        this.shops = shops;
        this.access = access;
    }

    @GetMapping
    public List<SupportTicket> list(Authentication auth) {
        AppUser user = access.requireUser(auth);
        if (user.getRole() == Role.ADMIN) {
            return tickets.findAllByOrderByCreatedAtDesc();
        }
        if (user.getRole() == Role.SELLER) {
            List<SupportTicket> result = new ArrayList<>(tickets.findByBuyerIdOrderByCreatedAtDesc(user.getId()));
            shops.findByOwnerUserId(user.getId())
                    .forEach(shop -> result.addAll(tickets.findByShopIdOrderByCreatedAtDesc(shop.getId())));
            return result.stream().distinct().toList();
        }
        return tickets.findByBuyerIdOrderByCreatedAtDesc(user.getId());
    }

    @GetMapping("/{id}")
    public SupportTicket one(Authentication auth, @PathVariable String id) {
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanView(access.requireUser(auth), ticket);
        return ticket;
    }

    @PostMapping
    @Transactional
    public SupportTicket create(Authentication auth, @Valid @RequestBody CreateTicketRequest request) {
        AppUser user = access.requireUser(auth);
        Instant now = Instant.now();
        SupportTicket ticket = new SupportTicket();
        ticket.setId(Ids.next("tk"));
        ticket.setKind(request.kind() == null ? TicketKind.SUPPORT : request.kind());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setSubject(request.subject().trim());
        ticket.setBuyerId(user.getId());
        ticket.setShopId(blankToNull(request.shopId()));
        ticket.setOrderId(blankToNull(request.orderId()));
        ticket.setListingId(blankToNull(request.listingId()));
        ticket.setCreatedAt(now);
        ticket.setHidden(false);
        TicketMessage message = new TicketMessage();
        message.setId(Ids.next("m"));
        message.setTicket(ticket);
        message.setAuthorId(user.getId());
        message.setBody(request.body() == null ? "" : request.body());
        message.setCreatedAt(now);
        if (request.imageUrls() != null) {
            message.getImageUrls().addAll(request.imageUrls());
        }
        ticket.getMessages().add(message);
        return tickets.save(ticket);
    }

    @PostMapping("/{id}/messages")
    @Transactional
    public SupportTicket addMessage(
            Authentication auth, @PathVariable String id, @RequestBody TicketMessageRequest request) {
        AppUser user = access.requireUser(auth);
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanView(user, ticket);
        String body = request.body() == null ? "" : request.body().trim();
        List<String> images = request.imageUrls() == null ? List.of() : request.imageUrls();
        if (body.isBlank() && images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a reply or attach a picture");
        }
        TicketMessage message = new TicketMessage();
        message.setId(Ids.next("m"));
        message.setTicket(ticket);
        message.setAuthorId(user.getId());
        message.setBody(body);
        message.setCreatedAt(Instant.now());
        message.getImageUrls().addAll(images);
        ticket.getMessages().add(message);
        if (user.getRole() != Role.BUYER && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        if (user.getRole() != Role.BUYER && ticket.getAssignedToUserId() == null) {
            ticket.setAssignedToUserId(user.getId());
        }
        return tickets.save(ticket);
    }

    @PatchMapping("/{id}")
    @Transactional
    public SupportTicket patch(Authentication auth, @PathVariable String id, @RequestBody TicketPatch request) {
        AppUser user = access.requireUser(auth);
        SupportTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanManage(user, ticket);
        if (request.status() != null) {
            ticket.setStatus(request.status());
        }
        if (request.assignedToUserId() != null) {
            ticket.setAssignedToUserId(
                    request.assignedToUserId().isBlank() ? null : request.assignedToUserId());
        }
        if (request.hidden() != null) {
            ticket.setHidden(request.hidden());
            if (request.hidden()) {
                ticket.setStatus(TicketStatus.CLOSED);
            }
        }
        return tickets.save(ticket);
    }

    private void assertCanView(AppUser user, SupportTicket ticket) {
        if (user.getRole() == Role.ADMIN || user.getId().equals(ticket.getBuyerId())) {
            return;
        }
        if (ticket.getShopId() != null && ownsShop(user, ticket.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void assertCanManage(AppUser user, SupportTicket ticket) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (ticket.getShopId() != null && ownsShop(user, ticket.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private boolean ownsShop(AppUser user, String shopId) {
        return shops.findById(shopId)
                .map(shop -> shop.getOwnerUserId().equals(user.getId()))
                .orElse(false);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
