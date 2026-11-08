import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildCourtPayload,
  buildHearingPayload,
  buildJudgePayload,
  buildJudicialCasePayload,
  caseTransitions,
  courtLabel,
  describeJudicialError,
  emptyCourtForm,
  emptyHearingForm,
  emptyJudgeForm,
  emptyJudicialCaseForm,
  flattenHearings,
  formatDateTime,
  formatDay,
  groupHearings,
  hearingTransitions,
  judgeName,
  nextHearing,
  validateCourt,
  validateHearing,
  validateJudge,
  validateJudicialCase
} from './judicial.js';

// Heures locales sans fuseau : les tests ne dépendent pas de la machine.
const NOW = new Date('2026-10-18T10:00:00');

test('transitions d\'une procédure : celles du serveur, sans « audience programmée » (elle vient de la planification)', () => {
  assert.deepEqual(caseTransitions('REGISTERED'), ['IN_PROGRESS']);
  assert.deepEqual(caseTransitions('IN_PROGRESS'), ['DELIBERATION', 'CLOSED']);
  assert.deepEqual(caseTransitions('HEARING_SCHEDULED'), ['DELIBERATION', 'CLOSED']);
  assert.deepEqual(caseTransitions('DELIBERATION'), ['JUDGMENT_ISSUED', 'CLOSED']);
  assert.deepEqual(caseTransitions('JUDGMENT_ISSUED'), ['CLOSED']);
  assert.deepEqual(caseTransitions('CLOSED'), []);
  assert.deepEqual(caseTransitions('INCONNU'), []);
});

test('transitions d\'une audience', () => {
  assert.deepEqual(hearingTransitions('SCHEDULED'), ['COMPLETED', 'POSTPONED', 'CANCELLED']);
  assert.deepEqual(hearingTransitions('POSTPONED'), ['SCHEDULED', 'CANCELLED']);
  assert.deepEqual(hearingTransitions('COMPLETED'), []);
  assert.deepEqual(hearingTransitions('CANCELLED'), []);
});

test('audiences : à venir, à traiter (passées non traitées et reportées), historique', () => {
  const list = [
    { id: 'far', status: 'SCHEDULED', dateTime: '2026-11-30T09:00:00' },
    { id: 'near', status: 'SCHEDULED', dateTime: '2026-10-20T09:00:00' },
    { id: 'forgot', status: 'SCHEDULED', dateTime: '2026-10-10T09:00:00' },
    { id: 'postponed', status: 'POSTPONED', dateTime: '2026-10-25T09:00:00' },
    { id: 'done-old', status: 'COMPLETED', dateTime: '2026-09-01T09:00:00' },
    { id: 'done-new', status: 'COMPLETED', dateTime: '2026-10-15T09:00:00' },
    { id: 'off', status: 'CANCELLED', dateTime: '2026-10-22T09:00:00' }
  ];
  const groups = groupHearings(list, NOW);
  assert.deepEqual(groups.upcoming.map((item) => item.id), ['near', 'far']);
  assert.deepEqual(groups.toHandle.map((item) => item.id), ['forgot', 'postponed']);
  assert.deepEqual(groups.history.map((item) => item.id), ['off', 'done-new', 'done-old']);
  assert.equal(nextHearing(list, NOW).id, 'near');
  assert.equal(nextHearing([], NOW), null);
  assert.deepEqual(groupHearings(undefined, NOW), { upcoming: [], toHandle: [], history: [] });
});

test('audiences de toutes les procédures, rattachées à leur procédure', () => {
  const cases = [{ id: 'j1' }, { id: 'j2' }, { id: 'j3' }];
  const byCase = { j1: [{ id: 'h1' }], j2: [{ id: 'h2' }, { id: 'h3' }] };
  const all = flattenHearings(cases, byCase);
  assert.deepEqual(all.map((item) => [item.id, item.judicialCaseId]), [['h1', 'j1'], ['h2', 'j2'], ['h3', 'j2']]);
  assert.deepEqual(flattenHearings(undefined, undefined), []);
});

test('tribunal : champs obligatoires et corps de requête', () => {
  assert.deepEqual(Object.keys(validateCourt(emptyCourtForm())).sort(), ['city', 'name', 'type']);
  const form = { name: '  TPI Casablanca ', type: ' Tribunal de première instance ', city: ' Casablanca ', address: '   ' };
  assert.deepEqual(validateCourt(form), {});
  assert.deepEqual(buildCourtPayload(form), {
    name: 'TPI Casablanca', type: 'Tribunal de première instance', city: 'Casablanca', address: null
  });
});

test('juge : champs obligatoires et corps de requête', () => {
  assert.deepEqual(Object.keys(validateJudge(emptyJudgeForm())).sort(), ['courtId', 'firstName', 'lastName', 'role']);
  const form = { courtId: 'c1', firstName: ' Anne ', lastName: ' Martin ', role: ' Présidente ' };
  assert.deepEqual(validateJudge(form), {});
  assert.deepEqual(buildJudgePayload(form), { courtId: 'c1', firstName: 'Anne', lastName: 'Martin', role: 'Présidente' });
});

test('procédure : champs obligatoires, corps avec cabinet du jeton et statut « enregistrée »', () => {
  const empty = emptyJudicialCaseForm('2026-10-18');
  assert.deepEqual(Object.keys(validateJudicialCase(empty)).sort(), ['caseId', 'courtCaseNumber', 'courtId', 'judgeId']);
  const form = { ...empty, caseId: 'k1', courtId: 'c1', judgeId: 'j1', courtCaseNumber: ' 2026/123 ' };
  assert.deepEqual(validateJudicialCase(form), {});
  assert.deepEqual(buildJudicialCasePayload(form, 'firm'), {
    lawFirmId: 'firm', caseId: 'k1', courtId: 'c1', judgeId: 'j1', courtCaseNumber: '2026/123',
    procedureType: 'CIVIL', status: 'REGISTERED', openingDate: '2026-10-18'
  });
});

test('audience : date obligatoire, corps au format serveur, champs vides en null', () => {
  assert.ok(validateHearing(emptyHearingForm()).dateTime);
  const form = { dateTime: '2026-11-03T09:30', type: 'PLEADING', location: ' Salle 4 ', notes: '', nextAction: '  ' };
  assert.deepEqual(validateHearing(form), {});
  assert.deepEqual(buildHearingPayload(form), {
    dateTime: '2026-11-03T09:30:00', type: 'PLEADING', location: 'Salle 4', notes: null, nextAction: null
  });
});

test('libellés et formats', () => {
  assert.equal(judgeName({ firstName: 'Anne', lastName: 'Martin' }), 'Anne Martin');
  assert.equal(judgeName(null), '');
  assert.equal(courtLabel({ name: 'TPI', city: 'Rabat' }), 'TPI — Rabat');
  assert.equal(formatDay('2026-11-02'), '2 novembre 2026');
  assert.equal(formatDay(null), '—');
  assert.equal(formatDateTime('2026-10-20T14:30:00'), 'mar. 20 octobre 2026 à 14h30');
});

test('messages d\'erreur compréhensibles', () => {
  assert.match(describeJudicialError({ status: 403 }), /pas le droit/);
  assert.match(describeJudicialError({ status: 404 }), /introuvable/);
  assert.match(describeJudicialError({ status: 409, message: 'A hearing cannot be scheduled on a closed judicial case.' }), /clôturée/);
  assert.match(describeJudicialError({ status: 409, message: 'Invalid transition' }), /statut n'est pas autorisé/);
  assert.match(describeJudicialError({ status: 400 }), /juge doit appartenir/);
  assert.match(describeJudicialError({ status: 503 }), /indisponible/);
});
