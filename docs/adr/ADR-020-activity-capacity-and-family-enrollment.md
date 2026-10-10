# ADR-020 — Capacité d'activité et inscription famille

Accepté — Milestone 23.

L'inscription famille revalide le lien parent–enfant et le drapeau `allow_parent_enrollment`. La capacité est verrouillée pendant la décision : une place crée l'inscription, sinon une position de liste d'attente si elle est autorisée.

Une inscription payante crée exactement une `billing_charge` `ACTIVITY`, reliée par `activity_charge`. Capacité, attente et finance restent auditables sans compteur client fiable.
