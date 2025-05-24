package me.merunko.PenguinGuard.Entry;

import java.util.Objects;

public record Entry(
        String id,
        String category,
        String name,
        String email,
        String username,
        String otherInfo,
        String password
) {

    public Entry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(category);
        Objects.requireNonNull(name);
        Objects.requireNonNull(password);
        email = email != null ? email : "";
        username = username != null ? username : "";
        otherInfo = otherInfo != null ? otherInfo : "";
    }
}