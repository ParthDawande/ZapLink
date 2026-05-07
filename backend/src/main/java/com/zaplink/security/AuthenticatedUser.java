package com.zaplink.security;

import com.zaplink.model.Role;

public record AuthenticatedUser(Long userId, String username, Role role) {}
