/**
 * AI Generation Diff - Simple diff viewer for comparing original vs edited content
 * Uses a basic line-by-line comparison approach
 */
(function($) {
    'use strict';

    var AJS = window.AJS || {};

    /**
     * Show diff between original and current content
     */
    function showDiff(sectionKey, originalContent, currentContent) {
        var diffHtml = generateDiffHtml(originalContent, currentContent);

        var dialogHtml = '<section id="ai-generation-diff-dialog" class="aui-dialog2 aui-dialog2-large aui-layer" ' +
            'role="dialog" aria-hidden="true" data-aui-remove-on-hide="true">' +
            '<header class="aui-dialog2-header">' +
            '<h2 class="aui-dialog2-header-main">' +
            AJS.I18n.getText('ai.generation.diff.title') + ' — ' + sectionKey +
            '</h2>' +
            '<a class="aui-dialog2-header-close">' +
            '<span class="aui-icon aui-icon-small aui-iconfont-close-dialog"></span>' +
            '</a>' +
            '</header>' +
            '<div class="aui-dialog2-content">' +
            '<div class="ai-gen-diff-container">' +
            '<div class="ai-gen-diff-legend">' +
            '<span class="ai-gen-diff-added-legend">&#9632; ' + AJS.I18n.getText('ai.generation.diff.added') + '</span>' +
            '<span class="ai-gen-diff-removed-legend">&#9632; ' + AJS.I18n.getText('ai.generation.diff.removed') + '</span>' +
            '<span class="ai-gen-diff-unchanged-legend">&#9632; ' + AJS.I18n.getText('ai.generation.diff.unchanged') + '</span>' +
            '</div>' +
            '<div class="ai-gen-diff-content">' + diffHtml + '</div>' +
            '</div>' +
            '</div>' +
            '<footer class="aui-dialog2-footer">' +
            '<div class="aui-dialog2-footer-actions">' +
            '<button class="aui-button ai-gen-diff-close">' + AJS.I18n.getText('ai.generation.preview.close') + '</button>' +
            '</div>' +
            '</footer>' +
            '</section>';

        $(dialogHtml).appendTo('body');
        var $dialog = $('#ai-generation-diff-dialog');
        AJS.dialog2($dialog).show();

        $dialog.on('click', '.ai-gen-diff-close, .aui-dialog2-header-close', function() {
            AJS.dialog2($dialog).hide();
            $dialog.remove();
        });
    }

    /**
     * Generate HTML diff from two text strings
     * Simple line-by-line comparison
     */
    function generateDiffHtml(original, current) {
        var origLines = stripHtml(original).split('\n');
        var currLines = stripHtml(current).split('\n');

        var html = '<table class="ai-gen-diff-table">';
        html += '<thead><tr><th>' + AJS.I18n.getText('ai.generation.diff.original') + '</th>' +
            '<th>' + AJS.I18n.getText('ai.generation.diff.modified') + '</th></tr></thead>';
        html += '<tbody>';

        var maxLen = Math.max(origLines.length, currLines.length);

        for (var i = 0; i < maxLen; i++) {
            var origLine = i < origLines.length ? escapeHtml(origLines[i]) : '';
            var currLine = i < currLines.length ? escapeHtml(currLines[i]) : '';

            var origClass = '';
            var currClass = '';

            if (i >= origLines.length) {
                currClass = 'ai-gen-diff-added';
            } else if (i >= currLines.length) {
                origClass = 'ai-gen-diff-removed';
            } else if (origLines[i] !== currLines[i]) {
                origClass = 'ai-gen-diff-removed';
                currClass = 'ai-gen-diff-added';
            }

            html += '<tr>';
            html += '<td class="' + origClass + '"><span class="ai-gen-diff-line-num">' + (i + 1) + '</span>' + origLine + '</td>';
            html += '<td class="' + currClass + '"><span class="ai-gen-diff-line-num">' + (i + 1) + '</span>' + currLine + '</td>';
            html += '</tr>';
        }

        html += '</tbody></table>';

        // Summary
        var changes = countChanges(origLines, currLines);
        html += '<div class="ai-gen-diff-summary">';
        html += '<span class="ai-gen-diff-added-count">+' + changes.added + ' ' + AJS.I18n.getText('ai.generation.diff.lines.added') + '</span> ';
        html += '<span class="ai-gen-diff-removed-count">-' + changes.removed + ' ' + AJS.I18n.getText('ai.generation.diff.lines.removed') + '</span> ';
        html += '<span>' + changes.unchanged + ' ' + AJS.I18n.getText('ai.generation.diff.lines.unchanged') + '</span>';
        html += '</div>';

        return html;
    }

    function countChanges(origLines, currLines) {
        var added = 0, removed = 0, unchanged = 0;
        var maxLen = Math.max(origLines.length, currLines.length);

        for (var i = 0; i < maxLen; i++) {
            if (i >= origLines.length) { added++; }
            else if (i >= currLines.length) { removed++; }
            else if (origLines[i] !== currLines[i]) { added++; removed++; }
            else { unchanged++; }
        }

        return {added: added, removed: removed, unchanged: unchanged};
    }

    function stripHtml(html) {
        if (!html) return '';
        var tmp = document.createElement('div');
        tmp.innerHTML = html;
        return tmp.textContent || tmp.innerText || '';
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    // Expose namespace
    window.AiGeneration = window.AiGeneration || {};
    window.AiGeneration.Diff = {
        showDiff: showDiff
    };

})(AJS.$);
