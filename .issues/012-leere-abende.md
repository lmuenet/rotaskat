---
titel: Abende ohne Runden lassen sich beenden und bleiben dauerhaft in der Liste
typ: UX
schwere: niedrig
bereich: Übersicht, Abend
status: erledigt
gefunden: 2026-09-27 (Abend vom 16.08.2026, 0 Runden)
---

## Beobachtung

In der Übersicht steht ein abgeschlossener Abend „16.08.2026 · 0 Runden ·
Alex +0 Johannes +0 Lars +0 Niko +0“. Solche Abende entstehen, wenn man einen
Abend anlegt, sich vertippt (falsche Sitzordnung) oder nur ausprobiert. Es gibt
keinen Weg, sie wieder loszuwerden. Die Rangliste ignoriert sie, die Liste aber nicht.

## Vorschlag

- „Abend beenden“ bei 0 Runden als „Abend verwerfen“ anbieten und die Session
  löschen (Tombstone, falls schon synchronisiert).
- Alternativ Abende ohne Runden in der Übersicht ausblenden.
- Eine falsche Sitzordnung korrigieren können, solange noch keine Runde
  gespielt wurde. Heute hilft nur beenden und neu anlegen.

## Umsetzung

- Im laufenden Abend ohne Runde heißt der Knopf „Abend verwerfen“; der Abend
  wird per Tombstone entfernt (`RotaskatRepository.discardSession`), danach
  geht es in die Übersicht.
- Beendete Abende ohne Runde (wie der vom 16.08.) bieten in der Abrechnung
  „Abend verwerfen“ statt „wieder öffnen“.
- Falsche Sitzordnung: verwerfen und neu anlegen – der Dialog sagt das. Ein
  eigenes Bearbeiten der Sitzordnung gibt es bewusst nicht.
