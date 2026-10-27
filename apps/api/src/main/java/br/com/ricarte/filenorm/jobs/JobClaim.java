package br.com.ricarte.filenorm.jobs;

import java.util.UUID;

/** Um job reivindicado por este worker. {@code attempt} funciona como token de fencing. */
public record JobClaim(UUID jobId, int attempt) {
}
