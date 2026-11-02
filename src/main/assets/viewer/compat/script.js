// Read-only Markdown preview for the Android editor's WebView.
// The host owns the title bar and pushes an in-memory document, so there is no
// file picker, drop target or status chrome here — only the renderer:
//   window.previewMarkdown(source, label, baseUrl)
// Rendering is delegated to markdown.js, the Markdown engine in this folder.
//
// Plain ES5 on purpose: this file is loaded by the Android WebView, which on
// Android 5 is Chromium 37 (no let/const, arrow functions, URLSearchParams,
// fetch, Object.assign, Promise-based File.text, ...).
(function () {
  'use strict';

  // The engine ships its own stylesheet (alerts, tables, footnotes, task lists, …).
  var engineStyle = document.createElement('style');
  engineStyle.textContent = renderMarkdown.css;
  document.head.appendChild(engineStyle);

  // Single newlines in the source render as line breaks, as the preview always did.
  var RENDER_OPTIONS = { breaks: true };

  var previewEl = document.getElementById('preview');

  // URL that relative images in the current document resolve against.
  var baseUrl = window.location.href;

  function showMarkdown(md, label, base) {
    baseUrl = base || window.location.href;
    var doc = renderMarkdown.parse(md, RENDER_OPTIONS);
    previewEl.innerHTML = doc.html;
    resolveImageSources();
    document.title = (doc.meta.title || label || 'Markdown preview') + ' — Markdown preview';
    window.scrollTo(0, 0);
  }

  function absolute(url) {
    if (typeof URL !== 'function') return null;
    try { return new URL(url, baseUrl).href; } catch (e) { return null; }
  }

  function hasScheme(url) {
    return /^[a-z][a-z0-9+.-]*:/i.test(url);
  }

  // Images in a rendered document are relative to the document, not to this page.
  function resolveImageSources() {
    var imgs = previewEl.querySelectorAll('img[src]');
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      var src = img.getAttribute('src');
      if (!src || hasScheme(src) || src.indexOf('//') === 0 || src.charAt(0) === '#') continue;
      var abs = absolute(src);
      if (abs) img.src = abs; // leave as written when it cannot be resolved
    }
  }

  window.previewMarkdown = function (source, label, base) {
    showMarkdown(String(source == null ? '' : source), label || '', base || null);
  };
})();
