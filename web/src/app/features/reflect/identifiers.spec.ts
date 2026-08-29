import { describe, expect, it } from 'vitest';
import { findIdentifiers } from './identifiers';

const kinds = (text: string) => findIdentifiers(text).map((warning) => warning.kind);

describe('findIdentifiers', () => {
  it('spots an NHI number', () => {
    expect(kinds('Discussed with ZZZ0016 about pacing.')).toContain('an NHI number');
  });

  it('spots a Medicare number', () => {
    expect(kinds('Card 2123 45670 1 was presented.')).toContain('a Medicare number');
  });

  it('spots a date of birth', () => {
    expect(kinds('Born 12/03/1984.')).toContain('a date of birth');
  });

  it('spots an email address', () => {
    expect(kinds('Follow up with ada@example.org.')).toContain('an email address');
  });

  it('spots a phone number', () => {
    expect(kinds('Called 021 555 0134 afterwards.')).toContain('a phone number');
  });

  it('reports each kind once, however many times it appears', () => {
    expect(kinds('ABC1234 and DEF5678')).toEqual(['an NHI number']);
  });
});

describe('findIdentifiers, on writing that should pass unremarked', () => {
  it('leaves an ordinary reflection alone', () => {
    const text =
      'I noticed I rushed the opening and did not check whether the pace suited them. ' +
      'Next time I will slow down and ask.';

    expect(findIdentifiers(text)).toEqual([]);
  });

  it('does not mistake a time of day for a phone number', () => {
    expect(kinds('The session ran from 14:30 to 15:20.')).toEqual([]);
  });

  it('does not mistake a dosage for an identifier', () => {
    expect(kinds('Titrated to 50mg over 3 weeks.')).toEqual([]);
  });

  it('does not flag a year on its own', () => {
    expect(kinds('We covered the 2019 guidance.')).toEqual([]);
  });

  it('does not flag clinical shorthand in capitals', () => {
    expect(kinds('Used CBT and ACT techniques throughout.')).toEqual([]);
  });
});
