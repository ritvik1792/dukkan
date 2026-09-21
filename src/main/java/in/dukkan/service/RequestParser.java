package in.dukkan.service;

public interface RequestParser {

    record ParsedRequest(String catalogProductId, String listingId, String queryText) {}

    ParsedRequest parse(String catalogProductId, String listingId, String queryText);
}
