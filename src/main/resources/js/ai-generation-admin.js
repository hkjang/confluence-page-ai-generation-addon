/**
 * AI Generation Admin - Admin configuration, glossary, forbidden words, policies, audit
 * Handles all admin REST interactions and UI updates
 */
(function($) {
    'use strict';

    var AJS = window.AJS || {};
    var REST_BASE = AJS.contextPath() + '/rest/ai-generation/1.0';

    AJS.toInit(function() {
        // Only initialize on admin pages
        if (!$('#ai-gen-admin-container').length) return;

        initTabs();
        loadConfig();
        loadGlossary();
        loadForbiddenWords();
        loadPolicies();
        loadAuditLog();
        loadUsage();
        bindEvents();
    });

    // ─────────────── Tabs ───────────────

    function initTabs() {
        $('.ai-gen-admin-tab').on('click', function(e) {
            e.preventDefault();
            var tab = $(this).data('tab');
            $('.ai-gen-admin-tab').removeClass('active');
            $(this).addClass('active');
            $('.ai-gen-admin-panel').hide();
            $('#ai-gen-panel-' + tab).show();
        });
    }

    // ─────────────── Config ───────────────

    function loadConfig() {
        $.ajax({
            url: REST_BASE + '/admin/config',
            dataType: 'json',
            success: function(config) {
                $('#ai-gen-vllm-endpoint').val(config.vllmEndpoint || '');
                $('#ai-gen-model').val(config.model || '');
                $('#ai-gen-max-tokens').val(config.maxTokensPerRequest || 4096);
                $('#ai-gen-temperature').val(config.defaultTemperature || 0.7);
                $('#ai-gen-timeout').val(config.timeoutSeconds || 60);
                $('#ai-gen-max-concurrent').val(config.maxConcurrentRequests || 5);
                $('#ai-gen-storage-policy').val(config.storagePolicy || 'NO_STORE');
                $('#ai-gen-retention-days').val(config.auditRetentionDays || 30);
                $('#ai-gen-max-user-day').val(config.maxRequestsPerUserPerDay || 50);
                $('#ai-gen-max-space-day').val(config.maxRequestsPerSpacePerDay || 200);
                $('#ai-gen-api-key-status').text(config.apiKeySet ? AJS.I18n.getText('ai.generation.admin.apikey.set') : AJS.I18n.getText('ai.generation.admin.apikey.notset'));
            },
            error: function() {
                showAdminError(AJS.I18n.getText('ai.generation.admin.config.load.error'));
            }
        });
    }

    function saveConfig() {
        var config = {
            vllmEndpoint: $('#ai-gen-vllm-endpoint').val(),
            model: $('#ai-gen-model').val(),
            maxTokensPerRequest: parseInt($('#ai-gen-max-tokens').val()) || 4096,
            defaultTemperature: parseFloat($('#ai-gen-temperature').val()) || 0.7,
            timeoutSeconds: parseInt($('#ai-gen-timeout').val()) || 60,
            maxConcurrentRequests: parseInt($('#ai-gen-max-concurrent').val()) || 5,
            storagePolicy: $('#ai-gen-storage-policy').val(),
            auditRetentionDays: parseInt($('#ai-gen-retention-days').val()) || 30,
            maxRequestsPerUserPerDay: parseInt($('#ai-gen-max-user-day').val()) || 50,
            maxRequestsPerSpacePerDay: parseInt($('#ai-gen-max-space-day').val()) || 200
        };

        $.ajax({
            url: REST_BASE + '/admin/config',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(config),
            dataType: 'json',
            success: function() {
                AJS.flag({type: 'success', title: AJS.I18n.getText('ai.generation.admin.config.saved'), close: 'auto'});
            },
            error: function() {
                AJS.flag({type: 'error', title: AJS.I18n.getText('ai.generation.admin.config.save.error'), close: 'auto'});
            }
        });
    }

    function saveApiKey() {
        var apiKey = $('#ai-gen-api-key').val();
        if (!apiKey) {
            AJS.flag({type: 'warning', title: AJS.I18n.getText('ai.generation.admin.apikey.required'), close: 'auto'});
            return;
        }

        $.ajax({
            url: REST_BASE + '/admin/config/apikey',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify({apiKey: apiKey}),
            dataType: 'json',
            success: function() {
                $('#ai-gen-api-key').val('');
                $('#ai-gen-api-key-status').text(AJS.I18n.getText('ai.generation.admin.apikey.set'));
                AJS.flag({type: 'success', title: AJS.I18n.getText('ai.generation.admin.apikey.saved'), close: 'auto'});
            },
            error: function() {
                AJS.flag({type: 'error', title: AJS.I18n.getText('ai.generation.admin.apikey.save.error'), close: 'auto'});
            }
        });
    }

    function testConnection() {
        $('#ai-gen-test-result').html(AiGeneration.Components.spinner());

        $.ajax({
            url: REST_BASE + '/health/vllm',
            dataType: 'json',
            success: function(result) {
                $('#ai-gen-test-result').html(
                    AiGeneration.Admin.connectionResult({
                        connected: result.connected,
                        endpoint: result.endpoint || '',
                        model: result.model || '',
                        error: result.error
                    })
                );
            },
            error: function() {
                $('#ai-gen-test-result').html(
                    AiGeneration.Admin.connectionResult({
                        connected: false,
                        endpoint: '',
                        model: '',
                        error: AJS.I18n.getText('ai.generation.admin.test.error')
                    })
                );
            }
        });
    }

    // ─────────────── Glossary ───────────────

    function loadGlossary() {
        $.ajax({
            url: REST_BASE + '/admin/glossary',
            dataType: 'json',
            success: function(terms) {
                var $tbody = $('#ai-gen-glossary-table tbody').empty();
                $.each(terms, function(i, term) {
                    $tbody.append(AiGeneration.Admin.glossaryRow(term));
                });
            }
        });
    }

    function addGlossaryTerm() {
        var term = $('#ai-gen-new-term').val();
        var definition = $('#ai-gen-new-definition').val();
        var spaceKey = $('#ai-gen-new-term-space').val() || null;

        if (!term || !definition) {
            AJS.flag({type: 'warning', title: AJS.I18n.getText('ai.generation.admin.glossary.required'), close: 'auto'});
            return;
        }

        $.ajax({
            url: REST_BASE + '/admin/glossary',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({term: term, definition: definition, spaceKey: spaceKey}),
            dataType: 'json',
            success: function() {
                $('#ai-gen-new-term, #ai-gen-new-definition, #ai-gen-new-term-space').val('');
                loadGlossary();
                AJS.flag({type: 'success', title: AJS.I18n.getText('ai.generation.admin.glossary.added'), close: 'auto'});
            }
        });
    }

    // ─────────────── Forbidden Words ───────────────

    function loadForbiddenWords() {
        $.ajax({
            url: REST_BASE + '/admin/forbidden-words',
            dataType: 'json',
            success: function(words) {
                var $tbody = $('#ai-gen-forbidden-table tbody').empty();
                $.each(words, function(i, word) {
                    $tbody.append(AiGeneration.Admin.forbiddenRow(word));
                });
            }
        });
    }

    function addForbiddenWord() {
        var pattern = $('#ai-gen-new-pattern').val();
        var replacement = $('#ai-gen-new-replacement').val() || null;
        var isRegex = $('#ai-gen-new-isregex').is(':checked');
        var spaceKey = $('#ai-gen-new-word-space').val() || null;

        if (!pattern) {
            AJS.flag({type: 'warning', title: AJS.I18n.getText('ai.generation.admin.forbidden.required'), close: 'auto'});
            return;
        }

        $.ajax({
            url: REST_BASE + '/admin/forbidden-words',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({pattern: pattern, replacement: replacement, isRegex: isRegex, spaceKey: spaceKey}),
            dataType: 'json',
            success: function() {
                $('#ai-gen-new-pattern, #ai-gen-new-replacement, #ai-gen-new-word-space').val('');
                $('#ai-gen-new-isregex').prop('checked', false);
                loadForbiddenWords();
                AJS.flag({type: 'success', title: AJS.I18n.getText('ai.generation.admin.forbidden.added'), close: 'auto'});
            }
        });
    }

    // ─────────────── Policies ───────────────

    function loadPolicies() {
        $.ajax({
            url: REST_BASE + '/admin/policies',
            dataType: 'json',
            success: function(policies) {
                var $tbody = $('#ai-gen-policy-table tbody').empty();
                $.each(policies, function(i, policy) {
                    $tbody.append(AiGeneration.Admin.policyRow(policy));
                });
            }
        });
    }

    // ─────────────── Audit ───────────────

    var auditOffset = 0;
    var auditLimit = 50;

    function loadAuditLog() {
        var params = {
            offset: auditOffset,
            limit: auditLimit,
            userKey: $('#ai-gen-audit-user').val() || undefined,
            action: $('#ai-gen-audit-action').val() || undefined,
            spaceKey: $('#ai-gen-audit-space').val() || undefined
        };

        $.ajax({
            url: REST_BASE + '/admin/audit',
            data: params,
            dataType: 'json',
            success: function(result) {
                var $tbody = $('#ai-gen-audit-table tbody').empty();
                $.each(result.items, function(i, log) {
                    $tbody.append(AiGeneration.Admin.auditRow({
                        userKey: log.userKey,
                        action: log.action,
                        spaceKey: log.spaceKey || '-',
                        details: log.details || '',
                        timestamp: log.timestamp ? new Date(log.timestamp).toLocaleString() : '-'
                    }));
                });
                $('#ai-gen-audit-total').text(result.total);
                $('#ai-gen-audit-prev').prop('disabled', auditOffset === 0);
                $('#ai-gen-audit-next').prop('disabled', auditOffset + auditLimit >= result.total);
            }
        });
    }

    // ─────────────── Usage ───────────────

    function loadUsage() {
        var days = parseInt($('#ai-gen-usage-days').val()) || 7;

        $.ajax({
            url: REST_BASE + '/admin/usage',
            data: {days: days},
            dataType: 'json',
            success: function(summary) {
                $('#ai-gen-usage-container').html(
                    AiGeneration.Admin.usageSummary({
                        totalRequests: summary.totalRequests || 0,
                        totalTokens: summary.totalTokens || 0,
                        activeUsers: summary.activeUsers || 0,
                        activeSpaces: summary.activeSpaces || 0,
                        period: days + ' ' + AJS.I18n.getText('ai.generation.admin.usage.days')
                    })
                );
            }
        });
    }

    // ─────────────── Events ───────────────

    function bindEvents() {
        // Config
        $(document).on('click', '#ai-gen-save-config', saveConfig);
        $(document).on('click', '#ai-gen-save-apikey', saveApiKey);
        $(document).on('click', '#ai-gen-test-connection', testConnection);

        // Glossary
        $(document).on('click', '#ai-gen-add-glossary', addGlossaryTerm);
        $(document).on('click', '.ai-gen-delete-glossary', function() {
            var id = $(this).data('id');
            $.ajax({url: REST_BASE + '/admin/glossary/' + id, type: 'DELETE', success: loadGlossary});
        });

        // Forbidden words
        $(document).on('click', '#ai-gen-add-forbidden', addForbiddenWord);
        $(document).on('click', '.ai-gen-delete-forbidden', function() {
            var id = $(this).data('id');
            $.ajax({url: REST_BASE + '/admin/forbidden-words/' + id, type: 'DELETE', success: loadForbiddenWords});
        });

        // Policies
        $(document).on('click', '.ai-gen-delete-policy', function() {
            var spaceKey = $(this).data('space-key');
            $.ajax({url: REST_BASE + '/admin/policies/' + spaceKey, type: 'DELETE', success: loadPolicies});
        });

        // Audit
        $(document).on('click', '#ai-gen-audit-filter', function() { auditOffset = 0; loadAuditLog(); });
        $(document).on('click', '#ai-gen-audit-prev', function() { auditOffset = Math.max(0, auditOffset - auditLimit); loadAuditLog(); });
        $(document).on('click', '#ai-gen-audit-next', function() { auditOffset += auditLimit; loadAuditLog(); });

        // Usage
        $(document).on('change', '#ai-gen-usage-days', loadUsage);
    }

    function showAdminError(msg) {
        AJS.flag({type: 'error', title: msg, close: 'auto'});
    }

})(AJS.$);
