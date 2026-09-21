package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.repository.UserRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Access {

    private final UserRepository users;

    public Access(UserRepository users) {
        this.users = users;
    }

    public Optional<AppUser> findUser(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof String id) || "anonymousUser".equals(id)) {
            return Optional.empty();
        }
        return users.findById(id);
    }

    public String userId(Authentication auth) {
        return findUser(auth)
                .map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public AppUser requireUser(Authentication auth) {
        return users.findById(userId(auth))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public boolean isAdmin(AppUser user) {
        return user.getRole() == Role.ADMIN;
    }

    public boolean isSeller(AppUser user) {
        return user.getRole() == Role.SELLER || user.getRole() == Role.ADMIN;
    }

    public AppUser requireSellerOrAdmin(Authentication auth) {
        AppUser user = requireUser(auth);
        if (!isSeller(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }

    public AppUser requireAdmin(Authentication auth) {
        AppUser user = requireUser(auth);
        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return user;
    }
}
