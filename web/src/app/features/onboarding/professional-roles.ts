/**
 * Free text on the server; a fixed list in the client so the same role is not recorded five ways.
 * "Other" exists because a list of clinical roles is never complete.
 */
export const PROFESSIONAL_ROLES = [
  'Psychiatrist',
  'Clinical psychologist',
  'Mental health nurse',
  'Occupational therapist',
  'Social worker',
  'Peer support worker',
  'Care coordinator',
  'Support worker',
  'Researcher',
  'Service manager',
  'Other',
] as const;
