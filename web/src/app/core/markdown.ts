/**
 * Renders the Markdown subset that module sections are written in.
 *
 * <p>Safe by construction rather than by filtering. Every character of author input is HTML-escaped
 * first, so no tag an author writes can survive; only then is a fixed set of tags introduced, all
 * of them generated here. That is a smaller problem than parsing arbitrary HTML and trying to
 * remove the dangerous parts afterwards, which is what a sanitiser has to do.
 *
 * <p>The people with authoring rights are organisation administrators, whose accounts are the ones
 * worth stealing, and what they write renders in colleagues' browsers. This is the boundary.
 */

const ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (character) => ESCAPES[character]);
}

/**
 * Only these schemes. A bare `javascript:` link is the one way a link could still execute, and an
 * unrecognised scheme is rendered as text rather than silently dropped so the author can see it.
 */
function safeHref(url: string): string | null {
  const trimmed = url.trim();
  return /^(https?:\/\/|mailto:)/i.test(trimmed) ? trimmed : null;
}

/** Applies inline formatting to text that has already been escaped. */
function inline(escaped: string): string {
  return escaped
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>')
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (whole, label: string, url: string) => {
      // The url arrives escaped, so &amp; has to come back before it is judged and re-emitted.
      const href = safeHref(url.replace(/&amp;/g, '&'));
      return href
        ? `<a href="${escapeHtml(href)}" target="_blank" rel="noopener noreferrer">${label}</a>`
        : whole;
    });
}

export function renderMarkdown(source: string): string {
  const lines = (source ?? '').replace(/\r\n/g, '\n').split('\n');
  const html: string[] = [];

  let paragraph: string[] = [];
  let listTag: 'ul' | 'ol' | null = null;
  let inCodeBlock = false;
  let code: string[] = [];

  const closeParagraph = () => {
    if (paragraph.length > 0) {
      html.push(`<p>${inline(escapeHtml(paragraph.join(' ')))}</p>`);
      paragraph = [];
    }
  };
  const closeList = () => {
    if (listTag) {
      html.push(`</${listTag}>`);
      listTag = null;
    }
  };

  for (const line of lines) {
    if (line.trimStart().startsWith('```')) {
      if (inCodeBlock) {
        html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`);
        code = [];
      } else {
        closeParagraph();
        closeList();
      }
      inCodeBlock = !inCodeBlock;
      continue;
    }
    if (inCodeBlock) {
      code.push(line);
      continue;
    }

    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      closeParagraph();
      closeList();
      const level = heading[1].length + 1; // h1 belongs to the page, not to a section's content
      html.push(`<h${level}>${inline(escapeHtml(heading[2].trim()))}</h${level}>`);
      continue;
    }

    const bullet = /^\s*[-*]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    if (bullet || numbered) {
      closeParagraph();
      const wanted = bullet ? 'ul' : 'ol';
      if (listTag !== wanted) {
        closeList();
        html.push(`<${wanted}>`);
        listTag = wanted;
      }
      html.push(`<li>${inline(escapeHtml((bullet ?? numbered)![1]))}</li>`);
      continue;
    }

    if (line.trim() === '') {
      closeParagraph();
      closeList();
      continue;
    }
    paragraph.push(line.trim());
  }

  if (inCodeBlock) {
    html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`);
  }
  closeParagraph();
  closeList();
  return html.join('');
}
