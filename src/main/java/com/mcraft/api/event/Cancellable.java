package com.mcraft.api.event;

public interface Cancellable {
    boolean isCancelled();

    void setCancelled(boolean c);
}