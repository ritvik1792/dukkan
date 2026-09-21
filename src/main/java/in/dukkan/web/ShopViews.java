package in.dukkan.web;

import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopEmployee;
import in.dukkan.domain.ShopTransport;
import in.dukkan.repository.ShopEmployeeRepository;
import in.dukkan.repository.ShopTransportRepository;
import in.dukkan.web.dto.ShopDtos.ShopView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ShopViews {

    private final ShopEmployeeRepository employees;
    private final ShopTransportRepository transport;

    public ShopViews(ShopEmployeeRepository employees, ShopTransportRepository transport) {
        this.employees = employees;
        this.transport = transport;
    }

    public ShopView toView(Shop shop) {
        return ShopView.from(
                shop,
                employees.findByShopIdOrderByNameAsc(shop.getId()),
                transport.findByShopIdOrderByLabelAsc(shop.getId()));
    }

    public List<ShopView> toViews(List<Shop> shops) {
        if (shops.isEmpty()) {
            return List.of();
        }
        List<String> ids = shops.stream().map(Shop::getId).toList();
        Map<String, List<ShopEmployee>> employeesByShop = new LinkedHashMap<>();
        Map<String, List<ShopTransport>> transportByShop = new LinkedHashMap<>();
        for (String id : ids) {
            employeesByShop.put(id, new ArrayList<>());
            transportByShop.put(id, new ArrayList<>());
        }
        for (ShopEmployee employee : employees.findByShopIdInOrderByNameAsc(ids)) {
            employeesByShop.get(employee.getShopId()).add(employee);
        }
        for (ShopTransport vehicle : transport.findByShopIdInOrderByLabelAsc(ids)) {
            transportByShop.get(vehicle.getShopId()).add(vehicle);
        }
        return shops.stream()
                .map(shop -> ShopView.from(
                        shop,
                        employeesByShop.getOrDefault(shop.getId(), List.of()),
                        transportByShop.getOrDefault(shop.getId(), List.of())))
                .toList();
    }
}
