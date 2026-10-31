/**
 * Conversational search (ADR-005, ADR-009, WP2 §11):
 * {@link com.aresstack.serp4j.core.port.conversation.ConversationalSearchProvider} opens
 * {@link com.aresstack.serp4j.core.port.conversation.SearchSession}s whose turns keep the engine-side context.
 * The whole package is {@link com.aresstack.serp4j.core.annotation.Experimental} until the first adapter exists.
 */
@Experimental("conversational search stabilises with the first Google AI Mode adapter (Paket G)")
package com.aresstack.serp4j.core.port.conversation;

import com.aresstack.serp4j.core.annotation.Experimental;
