/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.android.systemui.fundamental.ambientmusic;

import java.util.function.Function;

import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowCollector;

/**
 * Minimal {@code map} / {@code onStart} for {@link Flow} written in plain Java, so the
 * Now Playing classes can mirror the stock Kotlin flow chains without inline/suspend interop.
 * Both operators are pure delegation: the collector runs on whatever context collects the flow.
 */
public final class AmbientIndicationFlows {

    private AmbientIndicationFlows() {
    }

    /** {@code source.map(mapper)}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Flow map(Flow source, Function<Object, Object> mapper) {
        return new Flow() {
            @Override
            public Object collect(FlowCollector collector, Continuation continuation) {
                return source.collect(
                        (FlowCollector) (value, cont) -> collector.emit(mapper.apply(value), cont),
                        continuation);
            }
        };
    }

    /** {@code source.onStart { action() }} for a non-suspending action. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Flow onStart(Flow source, Runnable action) {
        return new Flow() {
            @Override
            public Object collect(FlowCollector collector, Continuation continuation) {
                action.run();
                return source.collect(collector, continuation);
            }
        };
    }
}
