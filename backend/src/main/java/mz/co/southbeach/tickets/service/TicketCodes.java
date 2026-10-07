package mz.co.southbeach.tickets.service;

import java.security.SecureRandom;
import java.util.Base64;

/** Unguessable identifiers from a cryptographic random source. */
final class TicketCodes {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no 0/O/1/I: safe to type by hand

    private TicketCodes() { }

    /** 26 characters of a 32-letter alphabet = 130 bits. */
    static String ticketCode() {
        var chars = new char[26];
        for (int i = 0; i < chars.length; i++) chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        return new String(chars);
    }

    /** 192 bits, URL-safe. */
    static String accessToken() {
        var bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
