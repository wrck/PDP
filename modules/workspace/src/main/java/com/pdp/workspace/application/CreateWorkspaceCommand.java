package com.pdp.workspace.application;

import java.util.UUID;

public record CreateWorkspaceCommand(
    String code, String name, UUID ownerUserId, String defaultLocale, String defaultTimezone) {}
