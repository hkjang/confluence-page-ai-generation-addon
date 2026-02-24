/**
 * AI Generation Preview - Handles preview, section editing, saving
 * Shows generated content, allows section-by-section editing and regeneration
 */
(function($) {
    'use strict';

    var AJS = window.AJS || {};
    var REST_BASE = AJS.contextPath() + '/rest/ai-generation/1.0';
    var currentJobId = null;
    var currentSpaceKey = null;
    var generationResult = null;
    var originalSections = {};

    /**
     * Open preview dialog with generation results
     */
    function openPreview(jobId, templateKey, spaceKey) {
        currentJobId = jobId;
        currentSpaceKey = spaceKey;

        // Load result
        $.ajax({
            url: REST_BASE + '/generation/result/' + jobId,
            dataType: 'json',
            success: function(result) {
                generationResult = result;
                showPreviewDialog(result, templateKey);
            },
            error: function() {
                AJS.flag({type: 'error', title: AJS.I18n.getText('ai.generation.preview.load.error'), close: 'auto'});
            }
        });
    }

    /**
     * Show preview dialog with content
     */
    function showPreviewDialog(result, templateKey) {
        var dialogHtml = AiGeneration.Preview.dialog({
            jobId: currentJobId,
            templateName: templateKey
        });
        $(dialogHtml).appendTo('body');

        var $dialog = $('#ai-generation-preview-dialog');

        // Render sections
        renderSections(result.sections);

        // Render quality checks
        if (result.qualityCheck) {
            renderQualityPanel(result.qualityCheck);
        }

        // Render metadata
        if (result.metadata) {
            $('#ai-gen-preview-tokens').text(result.metadata.tokenCount || '-');
            $('#ai-gen-preview-duration').text(
                result.metadata.durationMs ? (result.metadata.durationMs / 1000).toFixed(1) + 's' : '-'
            );
        }

        // Update status
        $('#ai-gen-preview-status').text(result.status)
            .addClass(result.status === 'COMPLETED' ? 'aui-lozenge-success' : 'aui-lozenge-current');

        bindPreviewEvents($dialog);
        AJS.dialog2($dialog).show();
    }

    /**
     * Render sections into preview
     */
    function renderSections(sections) {
        var $container = $('#ai-gen-preview-sections').empty();
        var $nav = $('#ai-gen-preview-section-nav').empty();

        $.each(sections, function(i, section) {
            // Store original content
            originalSections[section.key] = section.content;

            // Render section block
            $container.append(AiGeneration.Preview.sectionBlock({
                key: section.key,
                title: section.title,
                content: section.content || '',
                status: section.status,
                index: i
            }));

            // Render nav item
            var statusIcon = section.status === 'COMPLETED' ? 'aui-iconfont-approve' :
                            section.status === 'FAILED' ? 'aui-iconfont-error' : 'aui-iconfont-time';
            $nav.append('<li data-section-key="' + section.key + '">' +
                '<a href="#section-' + section.key + '">' +
                '<span class="aui-icon aui-icon-small ' + statusIcon + '"></span> ' +
                section.title + '</a></li>');
        });
    }

    /**
     * Render quality check panel
     */
    function renderQualityPanel(quality) {
        if (!quality || !quality.hasIssues) return;

        var $panel = $('#ai-gen-quality-panel').show();
        var $warnings = $('#ai-gen-quality-warnings').empty();

        if (quality.warnings) {
            $.each(quality.warnings, function(i, warning) {
                $warnings.append(AiGeneration.Preview.qualityWarning({
                    type: 'warning',
                    message: warning
                }));
            });
        }

        if (quality.missingSections && quality.missingSections.length > 0) {
            $warnings.append(AiGeneration.Preview.qualityWarning({
                type: 'error',
                message: AJS.I18n.getText('ai.generation.preview.missing.sections') + ': ' +
                    quality.missingSections.join(', ')
            }));
        }

        if (quality.sensitivePatterns && quality.sensitivePatterns.length > 0) {
            $warnings.append(AiGeneration.Preview.qualityWarning({
                type: 'error',
                message: AJS.I18n.getText('ai.generation.preview.sensitive.detected') + ' (' +
                    quality.sensitivePatterns.length + ')'
            }));
        }
    }

    /**
     * Bind preview dialog events
     */
    function bindPreviewEvents($dialog) {
        // Section editing
        $dialog.on('click', '.ai-gen-edit-section', function() {
            var sectionKey = $(this).data('section-key');
            toggleSectionEditor(sectionKey);
        });

        // Save section edit
        $dialog.on('click', '.ai-gen-editor-save', function() {
            var sectionKey = $(this).data('section-key');
            saveSectionEdit(sectionKey);
        });

        // Cancel section edit
        $dialog.on('click', '.ai-gen-editor-cancel', function() {
            var sectionKey = $(this).data('section-key');
            cancelSectionEdit(sectionKey);
        });

        // Show diff
        $dialog.on('click', '.ai-gen-editor-diff', function() {
            var sectionKey = $(this).data('section-key');
            showSectionDiff(sectionKey);
        });

        // Regenerate section
        $dialog.on('click', '.ai-gen-regenerate-section', function() {
            var sectionKey = $(this).data('section-key');
            regenerateSection(sectionKey);
        });

        // Section navigation
        $dialog.on('click', '.ai-gen-section-nav a', function(e) {
            e.preventDefault();
            var sectionKey = $(this).closest('li').data('section-key');
            var $section = $('.ai-gen-section-block[data-section-key="' + sectionKey + '"]');
            if ($section.length) {
                $dialog.find('.aui-dialog2-content').animate({
                    scrollTop: $section.position().top
                }, 300);
            }
        });

        // Save as new page
        $dialog.on('click', '#ai-gen-preview-save', function() {
            showSaveDialog();
        });

        // Insert into existing page
        $dialog.on('click', '#ai-gen-preview-insert', function() {
            showInsertDialog();
        });

        // Close
        $dialog.on('click', '#ai-gen-preview-close', function() {
            closePreview();
        });
    }

    // ─────────────── Section Editing ───────────────

    function toggleSectionEditor(sectionKey) {
        var $block = $('.ai-gen-section-block[data-section-key="' + sectionKey + '"]');
        var $content = $block.find('.ai-gen-section-content');
        var $existing = $block.find('.ai-gen-section-editor');

        if ($existing.length) {
            $existing.remove();
            $content.show();
        } else {
            $content.hide();
            var currentContent = $content.html();
            $content.after(AiGeneration.Preview.sectionEditor({
                key: sectionKey,
                content: currentContent
            }));
        }
    }

    function saveSectionEdit(sectionKey) {
        var $editor = $('.ai-gen-section-editor[data-section-key="' + sectionKey + '"]');
        var newContent = $editor.find('textarea').val();

        $.ajax({
            url: REST_BASE + '/preview/section',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify({
                jobId: currentJobId,
                sectionKey: sectionKey,
                content: newContent
            }),
            dataType: 'json',
            success: function(result) {
                var $block = $('.ai-gen-section-block[data-section-key="' + sectionKey + '"]');
                $block.find('.ai-gen-section-content').html(result.content).show();
                $editor.remove();

                // Update in result
                updateSectionInResult(sectionKey, result.content);

                AJS.flag({type: 'success', title: AJS.I18n.getText('ai.generation.preview.section.saved'), close: 'auto'});
            },
            error: function() {
                AJS.flag({type: 'error', title: AJS.I18n.getText('ai.generation.preview.section.save.error'), close: 'auto'});
            }
        });
    }

    function cancelSectionEdit(sectionKey) {
        var $block = $('.ai-gen-section-block[data-section-key="' + sectionKey + '"]');
        $block.find('.ai-gen-section-editor').remove();
        $block.find('.ai-gen-section-content').show();
    }

    function showSectionDiff(sectionKey) {
        var $editor = $('.ai-gen-section-editor[data-section-key="' + sectionKey + '"]');
        var currentContent = $editor.find('textarea').val();
        var originalContent = originalSections[sectionKey] || '';
        AiGeneration.Diff.showDiff(sectionKey, originalContent, currentContent);
    }

    function regenerateSection(sectionKey) {
        AJS.flag({type: 'info', title: AJS.I18n.getText('ai.generation.preview.regenerating'), close: 'auto'});

        $.ajax({
            url: REST_BASE + '/generation/retry/' + currentJobId + '?sections=' + sectionKey,
            type: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            success: function(data) {
                AJS.flag({type: 'success',
                    title: AJS.I18n.getText('ai.generation.preview.regenerate.started'),
                    close: 'auto'});
                // Could poll for the single section here
            },
            error: function() {
                AJS.flag({type: 'error', title: AJS.I18n.getText('ai.generation.preview.regenerate.error'), close: 'auto'});
            }
        });
    }

    // ─────────────── Save/Insert ───────────────

    function showSaveDialog() {
        var saveHtml = AiGeneration.Preview.saveDialog({spaceKey: currentSpaceKey});
        $(saveHtml).appendTo('body');
        var $save = $('#ai-generation-save-dialog');
        AJS.dialog2($save).show();

        $save.on('click', '#ai-gen-save-confirm', function() {
            var title = $.trim($('#ai-gen-save-title').val());
            if (!title) {
                AJS.flag({type: 'warning', title: AJS.I18n.getText('ai.generation.preview.enter.title'), close: 'auto'});
                return;
            }
            savePage(title);
            AJS.dialog2($save).hide();
            $save.remove();
        });

        $save.on('click', '#ai-gen-save-cancel', function() {
            AJS.dialog2($save).hide();
            $save.remove();
        });
    }

    function savePage(title) {
        var sections = getCurrentSections();

        $.ajax({
            url: REST_BASE + '/preview/save',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                jobId: currentJobId,
                title: title,
                spaceKey: currentSpaceKey,
                parentPageId: parseInt($('#ai-gen-save-parent-id').val()) || 0,
                sections: sections
            }),
            dataType: 'json',
            success: function(result) {
                closePreview();
                AJS.flag({
                    type: 'success',
                    title: AJS.I18n.getText('ai.generation.preview.page.saved'),
                    body: '<a href="' + AJS.contextPath() + result.pageUrl + '">' + result.title + '</a>',
                    close: 'manual'
                });
                // Navigate to new page
                window.location.href = AJS.contextPath() + result.pageUrl;
            },
            error: function(xhr) {
                var errMsg = 'Save failed';
                try { errMsg = JSON.parse(xhr.responseText).error; } catch(e) {}
                AJS.flag({type: 'error', title: errMsg, close: 'auto'});
            }
        });
    }

    function showInsertDialog() {
        // Simple prompt for page ID
        var pageId = prompt(AJS.I18n.getText('ai.generation.preview.insert.page.id'));
        if (pageId) {
            insertIntoPage(parseInt(pageId));
        }
    }

    function insertIntoPage(pageId) {
        var sections = getCurrentSections();

        $.ajax({
            url: REST_BASE + '/preview/insert',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                jobId: currentJobId,
                pageId: pageId,
                sections: sections
            }),
            dataType: 'json',
            success: function(result) {
                closePreview();
                AJS.flag({
                    type: 'success',
                    title: AJS.I18n.getText('ai.generation.preview.inserted'),
                    close: 'auto'
                });
                window.location.href = AJS.contextPath() + result.pageUrl;
            },
            error: function(xhr) {
                var errMsg = 'Insert failed';
                try { errMsg = JSON.parse(xhr.responseText).error; } catch(e) {}
                AJS.flag({type: 'error', title: errMsg, close: 'auto'});
            }
        });
    }

    // ─────────────── Helpers ───────────────

    function getCurrentSections() {
        var sections = [];
        $('.ai-gen-section-block').each(function() {
            sections.push({
                key: $(this).data('section-key'),
                title: $(this).find('.ai-gen-section-header h3').text(),
                content: $(this).find('.ai-gen-section-content').html(),
                status: 'COMPLETED'
            });
        });
        return sections;
    }

    function updateSectionInResult(sectionKey, content) {
        if (generationResult && generationResult.sections) {
            $.each(generationResult.sections, function(i, s) {
                if (s.key === sectionKey) {
                    s.content = content;
                }
            });
        }
    }

    function closePreview() {
        var $dialog = $('#ai-generation-preview-dialog');
        if ($dialog.length) {
            AJS.dialog2($dialog).hide();
            $dialog.remove();
        }
    }

    // Expose namespace
    window.AiGeneration = window.AiGeneration || {};
    window.AiGeneration.Preview = window.AiGeneration.Preview || {};
    window.AiGeneration.Preview.openPreview = openPreview;
    window.AiGeneration.Preview.closePreview = closePreview;

})(AJS.$);
