package com.sebastianhauss.wayfare.dto;

/**
 * Internal result of an authentication flow: the freshly issued tokens (which
 * the controller writes into httpOnly cookies) plus the current-user view the
 * controller returns in the response body. Never serialized to the client.
 */
public record AuthResult(
        AuthResponse tokens,
        MeResponse user
) {
}
