/**
 * AI Generation Wizard - Main wizard logic
 * Handles 4-step wizard: Template → Settings → Context → Review/Generate
 */
(function($) {
    'use strict';

    var AJS = window.AJS || {};
    var REST_BASE = AJS.contextPath() + '/rest/ai-generation/1.0';
    var currentStep = 1;
    var totalSteps = 4;
    var selectedTemplate = null;
    var selectedPages = [];
    var selectedLabels = [];
    var spaceKey = '';

    // ─────────────── Initialization ───────────────

    function init() {
        bindTriggers();
    }

    function bindTriggers() {
        // Space menu trigger
        $(document).on('click', '#ai-generation-trigger, [data-ai-generation-trigger]', function(e) {
            e.preventDefault();
            spaceKey = $(this).data('space-key') || getSpaceKeyFromUrl();
            openWizard();
        });

        // Create menu trigger
        $(document).on('click', '#ai-generation-create-trigger', function(e) {
            e.preventDefault();
            spaceKey = AJS.Meta.get('space-key') || '';
            openWizard();
        });
    }

    function openWizard() {
        var dialogHtml = AiGeneration.Wizard.dialog({spaceKey: spaceKey});
        $(dialogHtml).appendTo('body');

        var $dialog = $('#ai-generation-wizard-dialog');
        AJS.dialog2($dialog).show();

        currentStep = 1;
        selectedTemplate = null;
        selectedPages = [];
        selectedLabels = [];

        bindWizardEvents($dialog);
        loadTemplates();
        updateStepDisplay();
    }

    function bindWizardEvents($dialog) {
        // Navigation buttons
        $dialog.on('click', '#ai-gen-wizard-next', nextStep);
        $dialog.on('click', '#ai-gen-wizard-prev', prevStep);
        $dialog.on('click', '#ai-gen-wizard-generate', startGeneration);
        $dialog.on('click', '#ai-gen-wizard-cancel', closeWizard);

        // Template selection
        $dialog.on('click', '.ai-gen-template-card', function() {
            $('.ai-gen-template-card').removeClass('selected');
            $(this).addClass('selected');
            selectedTemplate = $(this).data('template-key');
            $('#ai-gen-selected-template').val(selectedTemplate);
        });

        // Page search
        var pageSearchTimer;
        $dialog.on('keyup', '#ai-gen-page-search', function() {
            clearTimeout(pageSearchTimer);
            var query = $(this).val();
            pageSearchTimer = setTimeout(function() {
                searchPages(query);
            }, 300);
        });

        // Label search
        var labelSearchTimer;
        $dialog.on('keyup', '#ai-gen-label-search', function() {
            clearTimeout(labelSearchTimer);
            var query = $(this).val();
            labelSearchTimer = setTimeout(function() {
                searchLabels(query);
            }, 300);
        });

        // Remove selected items
        $dialog.on('click', '.ai-gen-remove-item', function() {
            var pageId = $(this).data('page-id');
            selectedPages = selectedPages.filter(function(p) { return p.id !== pageId; });
            $(this).closest('.ai-gen-picker-item').remove();
        });

        $dialog.on('click', '.ai-gen-remove-label', function() {
            var label = $(this).data('label');
            selectedLabels = selectedLabels.filter(function(l) { return l !== label; });
            $(this).closest('.ai-gen-picker-item').remove();
        });
    }

    // ─────────────── Step Navigation ───────────────

    function nextStep() {
        if (!validateStep(currentStep)) return;

        if (currentStep < totalSteps) {
            currentStep++;
            updateStepDisplay();

            if (currentStep === 4) {
                populateReviewStep();
            }
        }
    }

    function prevStep() {
        if (currentStep > 1) {
            currentStep--;
            updateStepDisplay();
        }
    }

    function updateStepDisplay() {
        // Hide all steps, show current
        $('.ai-gen-step').hide();
        $('.ai-gen-step[data-step="' + currentStep + '"]').show();

        // Update progress tracker
        $('.aui-progress-tracker-step').removeClass('aui-progress-tracker-step-current aui-progress-tracker-step-done');
        for (var i = 1; i <= totalSteps; i++) {
            var $step = $('.aui-progress-tracker-step[data-step="' + i + '"]');
            if (i < currentStep) {
                $step.addClass('aui-progress-tracker-step-done');
            } else if (i === currentStep) {
                $step.addClass('aui-progress-tracker-step-current');
            }
        }

        // Show/hide buttons
        $('#ai-gen-wizard-prev').toggle(currentStep > 1);
        $('#ai-gen-wizard-next').toggle(currentStep < totalSteps);
        $('#ai-gen-wizard-generate').toggle(currentStep === totalSteps);

        // Step label
        $('#ai-gen-wizard-step-label').text(
            AJS.I18n.getText('ai.generation.wizard.step.label', currentStep, totalSteps)
        );
    }

    function validateStep(step) {
        switch (step) {
            case 1:
                if (!selectedTemplate) {
                    AJS.flag({type: 'warning', title: AJS.I18n.getText('ai.generation.wizard.select.template'), close: 'auto'});
                    return false;
                }
                return true;
            case 2:
                var purpose = $.trim($('#ai-gen-purpose').val());
                if (!purpose) {
                    AJS.flag({type: 'warning', title: AJS.I18n.getText('ai.generation.wizard.enter.purpose'), close: 'auto'});
                    return false;
                }
                return true;
            case 3:
                return true; // Context is optional
            default:
                return true;
        }
    }

    // ─────────────── Data Loading ───────────────

    function loadTemplates() {
        $.ajax({
            url: REST_BASE + '/templates',
            data: {spaceKey: spaceKey},
            dataType: 'json',
            success: function(templates) {
                var $list = $('#ai-gen-template-list').empty();
                if (!templates || templates.length === 0) {
                    $list.html(AiGeneration.Components.emptyState({
                        message: AJS.I18n.getText('ai.generation.wizard.no.templates')
                    }));
                    return;
                }
                $.each(templates, function(i, tmpl) {
                    var cardHtml = AiGeneration.Wizard.templateCard({
                        key: tmpl.key,
                        name: AJS.I18n.getText(tmpl.nameI18nKey),
                        description: AJS.I18n.getText(tmpl.descriptionI18nKey),
                        iconClass: tmpl.iconClass || 'aui-iconfont-page-default',
                        sectionCount: tmpl.totalSectionCount
                    });
                    $list.append(cardHtml);
                });
            },
            error: function() {
                $('#ai-gen-template-list').html(AiGeneration.Components.errorPanel({
                    message: AJS.I18n.getText('ai.generation.wizard.load.error')
                }));
            }
        });
    }

    function searchPages(query) {
        if (!query || query.length < 2) {
            $('#ai-gen-page-results').empty();
            return;
        }
        $.ajax({
            url: REST_BASE + '/context/pages',
            data: {spaceKey: spaceKey, query: query, limit: 10},
            dataType: 'json',
            success: function(pages) {
                var $results = $('#ai-gen-page-results').empty();
                $.each(pages, function(i, page) {
                    if (selectedPages.some(function(p) { return p.id === page.id; })) return;
                    var $item = $('<div class="ai-gen-picker-result" data-page-id="' + page.id + '">' +
                        '<span class="aui-icon aui-icon-small aui-iconfont-page-default"></span> ' +
                        page.title + '</div>');
                    $item.on('click', function() {
                        selectedPages.push({id: page.id, title: page.title});
                        $('#ai-gen-selected-pages').append(
                            AiGeneration.Components.pageItem({id: page.id, title: page.title})
                        );
                        $(this).remove();
                    });
                    $results.append($item);
                });
            }
        });
    }

    function searchLabels(query) {
        if (!query || query.length < 1) {
            $('#ai-gen-label-results').empty();
            return;
        }
        $.ajax({
            url: REST_BASE + '/context/labels',
            data: {spaceKey: spaceKey, query: query, limit: 20},
            dataType: 'json',
            success: function(labels) {
                var $results = $('#ai-gen-label-results').empty();
                $.each(labels, function(i, label) {
                    if (selectedLabels.indexOf(label.name) >= 0) return;
                    var $item = $('<div class="ai-gen-picker-result"><span class="aui-lozenge">' +
                        label.name + '</span></div>');
                    $item.on('click', function() {
                        selectedLabels.push(label.name);
                        $('#ai-gen-selected-labels').append(
                            AiGeneration.Components.labelItem({name: label.name})
                        );
                        $(this).remove();
                    });
                    $results.append($item);
                });
            }
        });
    }

    // ─────────────── Review Step ───────────────

    function populateReviewStep() {
        $('#ai-gen-review-template').text(selectedTemplate);
        $('#ai-gen-review-purpose').text($('#ai-gen-purpose').val());
        $('#ai-gen-review-audience').text($('#ai-gen-audience option:selected').text());
        $('#ai-gen-review-tone').text($('#ai-gen-tone option:selected').text());
        $('#ai-gen-review-length').text($('#ai-gen-length option:selected').text());

        var contextInfo = [];
        if (selectedPages.length > 0) {
            contextInfo.push(selectedPages.length + ' ' + AJS.I18n.getText('ai.generation.wizard.pages'));
        }
        if (selectedLabels.length > 0) {
            contextInfo.push(selectedLabels.length + ' ' + AJS.I18n.getText('ai.generation.wizard.labels'));
        }
        if ($('#ai-gen-additional-context').val()) {
            contextInfo.push(AJS.I18n.getText('ai.generation.wizard.additional.context'));
        }
        $('#ai-gen-review-context').text(contextInfo.join(', ') || AJS.I18n.getText('ai.generation.wizard.no.context'));

        // Load section list
        $.ajax({
            url: REST_BASE + '/templates/' + selectedTemplate,
            dataType: 'json',
            success: function(template) {
                var $list = $('#ai-gen-section-list').empty();
                var allSections = (template.requiredSections || []).concat(template.recommendedSections || []);
                $.each(allSections, function(i, section) {
                    $list.append('<div class="ai-gen-review-section">' +
                        '<span class="aui-icon aui-icon-small ' +
                        (section.required ? 'aui-iconfont-approve' : 'aui-iconfont-add-small') +
                        '"></span> ' + section.defaultTitle +
                        (section.required ? ' <span class="aui-lozenge aui-lozenge-current">' +
                            AJS.I18n.getText('ai.generation.wizard.required') + '</span>' : '') +
                        '</div>');
                });
            }
        });
    }

    // ─────────────── Generation ───────────────

    function startGeneration() {
        var request = {
            spaceKey: spaceKey,
            templateKey: selectedTemplate,
            purpose: $('#ai-gen-purpose').val(),
            audience: $('#ai-gen-audience').val(),
            tone: $('#ai-gen-tone').val(),
            lengthPreference: $('#ai-gen-length').val(),
            contextPageIds: selectedPages.map(function(p) { return p.id; }),
            labels: selectedLabels,
            additionalContext: $('#ai-gen-additional-context').val(),
            parentPageId: parseInt($('#ai-gen-parent-page-id').val()) || 0
        };

        $('#ai-gen-wizard-generate').prop('disabled', true).spin();

        $.ajax({
            url: REST_BASE + '/generation/start',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(request),
            dataType: 'json',
            success: function(result) {
                closeWizard();
                AiGeneration.Polling.startPolling(result.jobId, selectedTemplate, spaceKey);
            },
            error: function(xhr) {
                $('#ai-gen-wizard-generate').prop('disabled', false).spinStop();
                var errMsg = 'Generation failed';
                try { errMsg = JSON.parse(xhr.responseText).error; } catch(e) {}
                AJS.flag({type: 'error', title: errMsg, close: 'auto'});
            }
        });
    }

    // ─────────────── Close ───────────────

    function closeWizard() {
        var $dialog = $('#ai-generation-wizard-dialog');
        if ($dialog.length) {
            AJS.dialog2($dialog).hide();
            $dialog.remove();
        }
    }

    // ─────────────── Utilities ───────────────

    function getSpaceKeyFromUrl() {
        var match = window.location.search.match(/spaceKey=([^&]+)/);
        if (match) return decodeURIComponent(match[1]);
        return AJS.Meta.get('space-key') || '';
    }

    // Initialize when DOM ready
    AJS.toInit(init);

    // Expose namespace
    window.AiGeneration = window.AiGeneration || {};
    window.AiGeneration.Wizard = window.AiGeneration.Wizard || {};
    window.AiGeneration.Wizard.open = openWizard;

})(AJS.$);
