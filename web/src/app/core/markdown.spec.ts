import { describe, expect, it } from 'vitest';
import { renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('formats the things an author will actually write', () => {
    const html = renderMarkdown('# Overview\n\nUse **care** and *judgement*.\n\n- First\n- Second');

    expect(html).toBe(
      '<h2>Overview</h2>' +
        '<p>Use <strong>care</strong> and <em>judgement</em>.</p>' +
        '<ul><li>First</li><li>Second</li></ul>',
    );
  });

  it('keeps numbered lists numbered', () => {
    expect(renderMarkdown('1. One\n2. Two')).toBe('<ol><li>One</li><li>Two</li></ol>');
  });

  it('links out safely', () => {
    expect(renderMarkdown('See [the guide](https://example.org/g).')).toContain(
      '<a href="https://example.org/g" target="_blank" rel="noopener noreferrer">the guide</a>',
    );
  });
});

describe('renderMarkdown, given an author trying it on', () => {
  it('never lets a script tag through', () => {
    const html = renderMarkdown('<script>alert(1)</script>');

    expect(html).not.toContain('<script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('escapes an image with an error handler into inert text', () => {
    const html = renderMarkdown('<img src=x onerror="alert(1)">');

    // The words survive as text, which is fine and is what the author will see. What must not
    // survive is a tag: an attribute only runs if a browser parses an element around it.
    expect(html).toBe('<p>&lt;img src=x onerror=&quot;alert(1)&quot;&gt;</p>');
    expect(html).not.toContain('<img');
  });

  it('refuses a javascript: link and shows it as text instead', () => {
    // eslint-disable-next-line no-script-url
    const html = renderMarkdown('[click me](javascript:alert(1))');

    expect(html).not.toContain('href="javascript');
    expect(html).toContain('[click me]');
  });

  it('refuses a data: link', () => {
    const html = renderMarkdown('[x](data:text/html;base64,PHNjcmlwdD4=)');

    expect(html).not.toContain('href="data:');
  });

  it('does not execute anything hidden in a code fence', () => {
    const html = renderMarkdown('```\n<script>alert(1)</script>\n```');

    expect(html).toBe('<pre><code>&lt;script&gt;alert(1)&lt;/script&gt;</code></pre>');
  });

  it('escapes quotes so nothing can break out of an attribute', () => {
    expect(renderMarkdown('a " and \' here')).toContain('&quot;');
  });
});
