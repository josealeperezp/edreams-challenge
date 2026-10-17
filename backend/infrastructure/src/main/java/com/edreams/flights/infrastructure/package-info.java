/**
 * Infrastructure: adapters that implement the application ports with concrete technology.
 * <ul>
 *   <li>{@code provider.*}: one anti-corruption layer per GDS provider (Alpha JSON, Beta XML, Gamma CSV)</li>
 *   <li>{@code persistence}: PostgreSQL via JdbcClient</li>
 *   <li>{@code cache}: Redis</li>
 *   <li>{@code web}: REST controllers and SSE</li>
 *   <li>{@code config}: configuration properties</li>
 * </ul>
 * May depend on application, domain and frameworks. Nothing depends on this module except bootstrap.
 */
package com.edreams.flights.infrastructure;
