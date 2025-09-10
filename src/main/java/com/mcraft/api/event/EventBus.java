package com.mcraft.api.event;

import java.lang.reflect.Method;
import java.util.*;

public final class EventBus {
    private static final class H {
        final Object o;
        final Method m;
        final int p;
        final Class<?> t;

        H(Object o, Method m, int p, Class<?> t) {
            this.o = o;
            this.m = m;
            this.p = p;
            this.t = t;
        }
    }

    private final Map<Class<?>, List<H>> map = new HashMap<>();

    public void register(Object listener) {
        for (Method m : listener.getClass().getMethods()) {
            if (!m.isAnnotationPresent(SubscribeEvent.class))
                continue;
            Class<?>[] ps = m.getParameterTypes();
            if (ps.length != 1 || !Event.class.isAssignableFrom(ps[0]))
                continue;
            m.setAccessible(true);
            SubscribeEvent a = m.getAnnotation(SubscribeEvent.class);
            map.computeIfAbsent(ps[0], k -> new ArrayList<>()).add(new H(listener, m, a.priority(), ps[0]));
        }
        for (List<H> hs : map.values())
            hs.sort((a, b) -> Integer.compare(b.p, a.p));
    }

    public void unregister(Object listener) {
        for (List<H> hs : map.values())
            hs.removeIf(h -> h.o == listener);
    }

    public void post(Event e) {
        Class<?> c = e.getClass();
// deliver to exact type and superclasses up to Event
        for (Map.Entry<Class<?>, List<H>> en : map.entrySet()) {
            if (!en.getKey().isAssignableFrom(c))
                continue;
            for (H h : en.getValue()) {
                try {
                    h.m.invoke(h.o, e);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }
}