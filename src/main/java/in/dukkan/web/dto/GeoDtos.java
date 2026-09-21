package in.dukkan.web.dto;

public class GeoDtos {

    public record ReverseGeocodeResponse(
            String formattedAddress,
            String houseNumber,
            String road,
            String suburb,
            String city,
            String state,
            String postcode,
            String country,
            double lat,
            double lng,
            String displayName) {}
}
