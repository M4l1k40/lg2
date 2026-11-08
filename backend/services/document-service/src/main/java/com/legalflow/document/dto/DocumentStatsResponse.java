package com.legalflow.document.dto;

/** Nombre de documents visibles par l'appelant : au total, depuis le début de la semaine glissante, et aujourd'hui. */
public record DocumentStatsResponse(long total, long lastSevenDays, long today) {
}
