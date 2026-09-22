const fs = require('fs');

const mdContent = fs.readFileSync('TESTING.md', 'utf8');

function mdToHtml(md) {
    let html = md;

    // Code blocks first
    html = html.replace(/```[\s\S]*?```/g, match => {
        const inner = match.replace(/^```[a-z]*\n?/, '').replace(/```$/, '');
        const escaped = inner
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        return `<pre><code>${escaped}</code></pre>`;
    });

    // Inline code
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Headers
    html = html.replace(/^#### (.+)$/gm, '<h4>$1</h4>');
    html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
    html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
    html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');

    // Bold and italic
    html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<strong><em>$1</em></strong>');
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

    // Horizontal rule
    html = html.replace(/^---$/gm, '<hr>');

    // Tables
    html = html.replace(/(\|.+\|\n\|[-| :]+\|\n(?:\|.+\|\n?)+)/g, tableMatch => {
        const rows = tableMatch.trim().split('\n');
        const header = rows[0];
        const body = rows.slice(2);
        const thCells = header.split('|').filter((c, i, a) => i > 0 && i < a.length - 1)
            .map(c => `<th>${c.trim()}</th>`).join('');
        const bodyRows = body.map(row => {
            const cells = row.split('|').filter((c, i, a) => i > 0 && i < a.length - 1)
                .map(c => `<td>${c.trim()}</td>`).join('');
            return `<tr>${cells}</tr>`;
        }).join('\n');
        return `<table><thead><tr>${thCells}</tr></thead><tbody>${bodyRows}</tbody></table>\n`;
    });

    // Checkboxes
    html = html.replace(/^- \[ \] (.+)$/gm, '<li class="todo">☐ $1</li>');
    html = html.replace(/^- \[x\] (.+)$/gm, '<li class="done">☑ $1</li>');

    // Unordered lists (with tree-like characters)
    html = html.replace(/((?:^[-*│├└─·] .+\n?)+)/gm, match => {
        const items = match.trim().split('\n')
            .map(line => {
                const text = line.replace(/^[-*] /, '').replace(/^[│├└─·\s]+/, '');
                const raw = line.replace(/^[-*] /, '');
                return `<li>${raw}</li>`;
            })
            .join('');
        return `<ul>${items}</ul>\n`;
    });

    // Ordered lists
    html = html.replace(/((?:^\d+\. .+\n?)+)/gm, match => {
        const items = match.trim().split('\n')
            .map(line => `<li>${line.replace(/^\d+\. /, '')}</li>`)
            .join('');
        return `<ol>${items}</ol>\n`;
    });

    // Links
    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');

    // Blockquotes
    html = html.replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>');

    // Paragraphs
    const lines = html.split('\n');
    const result = [];
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.match(/^<(h[1-6]|pre|ul|ol|li|table|thead|tbody|tr|th|td|hr|blockquote)/i)) {
            result.push(line);
        } else if (line.trim() === '') {
            result.push('');
        } else if (!line.startsWith('<')) {
            result.push(`<p>${line}</p>`);
        } else {
            result.push(line);
        }
    }
    return result.join('\n');
}

const body = mdToHtml(mdContent);

const htmlContent = `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>StaySync - Documentación de Testing</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    font-family: 'Segoe UI', Arial, sans-serif;
    font-size: 10.5pt;
    line-height: 1.65;
    color: #1a1a2e;
    padding: 22px 32px;
    max-width: 920px;
    margin: 0 auto;
  }
  h1 {
    font-size: 20pt;
    color: #16213e;
    border-bottom: 3px solid #0f3460;
    padding-bottom: 10px;
    margin: 28px 0 14px;
  }
  h2 {
    font-size: 14pt;
    color: #0f3460;
    border-bottom: 1.5px solid #e94560;
    padding-bottom: 5px;
    margin: 22px 0 10px;
  }
  h3 {
    font-size: 12pt;
    color: #e94560;
    margin: 16px 0 7px;
  }
  h4 {
    font-size: 10.5pt;
    color: #533483;
    margin: 12px 0 5px;
    font-weight: 700;
  }
  p { margin: 7px 0; }
  code {
    background: #f0f0f5;
    border: 1px solid #ddd;
    border-radius: 3px;
    padding: 1px 5px;
    font-family: 'Consolas', 'Courier New', monospace;
    font-size: 9pt;
    color: #c7254e;
  }
  pre {
    background: #1e1e2e;
    color: #cdd6f4;
    border-radius: 6px;
    padding: 12px 15px;
    overflow-x: auto;
    margin: 10px 0;
    page-break-inside: avoid;
    border-left: 4px solid #e94560;
  }
  pre code {
    background: transparent;
    border: none;
    color: #cdd6f4;
    font-size: 8.5pt;
    padding: 0;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    font-size: 9pt;
    page-break-inside: avoid;
  }
  th {
    background: #0f3460;
    color: #fff;
    padding: 7px 9px;
    text-align: left;
    font-weight: 600;
  }
  td {
    padding: 5px 9px;
    border-bottom: 1px solid #e8e8e8;
  }
  tr:nth-child(even) td { background: #f7f7fb; }
  ul, ol { margin: 6px 0 6px 20px; }
  li { margin: 2px 0; font-family: 'Consolas', monospace; font-size: 9pt; }
  li.todo, li.done { font-family: inherit; font-size: 10.5pt; }
  li.done { color: #27ae60; text-decoration: line-through; }
  hr { border: none; border-top: 2px solid #e94560; margin: 22px 0; }
  blockquote {
    border-left: 4px solid #0f3460;
    background: #f0f4ff;
    padding: 7px 13px;
    margin: 8px 0;
    font-style: italic;
  }
  a { color: #0f3460; }
  strong { font-weight: 700; }
  @media print {
    body { padding: 0; }
    pre, table { page-break-inside: avoid; }
    h2 { page-break-before: auto; }
  }
</style>
</head>
<body>
${body}
</body>
</html>`;

fs.writeFileSync('TESTING.html', htmlContent, 'utf8');
console.log('HTML generado: TESTING.html');
