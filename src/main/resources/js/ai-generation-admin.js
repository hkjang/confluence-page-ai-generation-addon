/**
 * AI Generation Admin - Config page JS
 * Handles: config load/save, API key, connection test, glossary, forbidden words
 *
 * NOTE: This JS is loaded via <script> tag (not via webResourceManager)
 * because the admin servlet does simple string replacement, not Velocity processing.
 * All UI is built with inline HTML - no Soy template dependencies.
 */
(function($) {
    'use strict';

    if (typeof AJS === 'undefined' || typeof AJS.$ === 'undefined') {
        // AJS not loaded yet, retry
        if (typeof jQuery !== 'undefined') {
            $ = jQuery;
        } else {
            console.error('[AI-Gen Admin] AJS/jQuery not available');
            return;
        }
    } else {
        $ = AJS.$;
    }

    // Wait for DOM ready
    $(function() {
        // Only initialize on config page
        if (!$('#ai-gen-config-form').length) return;

        var REST_BASE = (typeof AJS !== 'undefined' && AJS.contextPath ? AJS.contextPath() : '') + '/rest/ai-generation/1.0';

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
                    var keyStatus = config.apiKeySet ? '설정됨' : '미설정';
                    $('#ai-gen-api-key-status').text(keyStatus)
                        .toggleClass('aui-lozenge-success', !!config.apiKeySet)
                        .toggleClass('aui-lozenge-error', !config.apiKeySet);
                },
                error: function() {
                    showFlag('error', '설정을 불러올 수 없습니다.');
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
                    showFlag('success', '설정이 저장되었습니다.');
                },
                error: function() {
                    showFlag('error', '설정 저장에 실패했습니다.');
                }
            });
        }

        function saveApiKey() {
            var apiKey = $('#ai-gen-api-key').val();
            if (!apiKey) {
                showFlag('warning', 'API 키를 입력해 주세요.');
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
                    $('#ai-gen-api-key-status').text('설정됨').removeClass('aui-lozenge-error').addClass('aui-lozenge-success');
                    showFlag('success', 'API 키가 저장되었습니다.');
                },
                error: function() {
                    showFlag('error', 'API 키 저장에 실패했습니다.');
                }
            });
        }

        function testConnection() {
            $('#ai-gen-test-result').html('<div style="padding:10px;"><span class="aui-icon aui-icon-wait">로딩...</span> 연결 테스트 중...</div>');

            $.ajax({
                url: REST_BASE + '/health/vllm',
                dataType: 'json',
                timeout: 30000,
                success: function(result) {
                    var html;
                    if (result.connected) {
                        html = '<div class="ai-gen-connection-result ai-gen-connected">' +
                            '<span class="aui-icon aui-icon-small aui-iconfont-approve"></span> ' +
                            '<strong>연결됨</strong>' +
                            ' — ' + escapeHtml(result.endpoint || '') + ' (' + escapeHtml(result.model || '') + ')' +
                            '</div>';
                    } else {
                        html = '<div class="ai-gen-connection-result ai-gen-disconnected">' +
                            '<span class="aui-icon aui-icon-small aui-iconfont-error"></span> ' +
                            '<strong>연결 실패</strong>' +
                            (result.error ? '<p class="ai-gen-error-detail">' + escapeHtml(result.error) + '</p>' : '') +
                            '</div>';
                    }
                    $('#ai-gen-test-result').html(html);
                },
                error: function() {
                    $('#ai-gen-test-result').html(
                        '<div class="ai-gen-connection-result ai-gen-disconnected">' +
                        '<span class="aui-icon aui-icon-small aui-iconfont-error"></span> ' +
                        '<strong>연결 실패</strong>' +
                        '<p class="ai-gen-error-detail">연결 테스트에 실패했습니다. 서버 상태를 확인해 주세요.</p>' +
                        '</div>'
                    );
                }
            });
        }

        // ─────────────── Glossary ───────────────

        function glossaryRowHtml(term) {
            return '<tr data-term-id="' + term.id + '">' +
                '<td>' + escapeHtml(term.term) + '</td>' +
                '<td>' + escapeHtml(term.definition) + '</td>' +
                '<td>' + (term.spaceKey ? escapeHtml(term.spaceKey) : '<em>전역</em>') + '</td>' +
                '<td>' +
                '<button class="aui-button aui-button-subtle ai-gen-delete-glossary" data-id="' + term.id + '">' +
                '<span class="aui-icon aui-icon-small aui-iconfont-remove"></span>' +
                '</button>' +
                '</td></tr>';
        }

        function loadGlossary() {
            $.ajax({
                url: REST_BASE + '/admin/glossary',
                dataType: 'json',
                success: function(terms) {
                    var $tbody = $('#ai-gen-glossary-table tbody').empty();
                    $.each(terms, function(i, term) {
                        $tbody.append(glossaryRowHtml(term));
                    });
                }
            });
        }

        function addGlossaryTerm() {
            var term = $('#ai-gen-new-term').val();
            var definition = $('#ai-gen-new-definition').val();
            var spaceKey = $('#ai-gen-new-term-space').val() || null;

            if (!term || !definition) {
                showFlag('warning', '용어와 정의를 모두 입력해 주세요.');
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
                    showFlag('success', '용어가 추가되었습니다.');
                },
                error: function() {
                    showFlag('error', '용어 추가에 실패했습니다.');
                }
            });
        }

        // ─────────────── Forbidden Words ───────────────

        function forbiddenRowHtml(word) {
            return '<tr data-word-id="' + word.id + '">' +
                '<td>' + escapeHtml(word.pattern) + '</td>' +
                '<td>' + (word.replacement ? escapeHtml(word.replacement) : '<em>(삭제)</em>') + '</td>' +
                '<td>' + (word.isRegex ? '정규식' : '문자열') + '</td>' +
                '<td>' + (word.spaceKey ? escapeHtml(word.spaceKey) : '<em>전역</em>') + '</td>' +
                '<td>' +
                '<button class="aui-button aui-button-subtle ai-gen-delete-forbidden" data-id="' + word.id + '">' +
                '<span class="aui-icon aui-icon-small aui-iconfont-remove"></span>' +
                '</button>' +
                '</td></tr>';
        }

        function loadForbiddenWords() {
            $.ajax({
                url: REST_BASE + '/admin/forbidden-words',
                dataType: 'json',
                success: function(words) {
                    var $tbody = $('#ai-gen-forbidden-table tbody').empty();
                    $.each(words, function(i, word) {
                        $tbody.append(forbiddenRowHtml(word));
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
                showFlag('warning', '패턴을 입력해 주세요.');
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
                    showFlag('success', '금칙어가 추가되었습니다.');
                },
                error: function() {
                    showFlag('error', '금칙어 추가에 실패했습니다.');
                }
            });
        }

        // ─────────────── Events ───────────────

        // Config
        $(document).on('click', '#ai-gen-save-config', saveConfig);
        $(document).on('click', '#ai-gen-save-apikey', saveApiKey);
        $(document).on('click', '#ai-gen-test-connection', testConnection);

        // Glossary
        $(document).on('click', '#ai-gen-add-glossary', addGlossaryTerm);
        $(document).on('click', '.ai-gen-delete-glossary', function() {
            var id = $(this).data('id');
            if (confirm('용어를 삭제하시겠습니까?')) {
                $.ajax({url: REST_BASE + '/admin/glossary/' + id, type: 'DELETE', success: loadGlossary});
            }
        });

        // Forbidden words
        $(document).on('click', '#ai-gen-add-forbidden', addForbiddenWord);
        $(document).on('click', '.ai-gen-delete-forbidden', function() {
            var id = $(this).data('id');
            if (confirm('금칙어를 삭제하시겠습니까?')) {
                $.ajax({url: REST_BASE + '/admin/forbidden-words/' + id, type: 'DELETE', success: loadForbiddenWords});
            }
        });

        // ─────────────── Helpers ───────────────

        function showFlag(type, title) {
            if (typeof AJS !== 'undefined' && AJS.flag) {
                AJS.flag({type: type, title: title, close: 'auto'});
            } else {
                alert(title);
            }
        }

        function escapeHtml(str) {
            if (!str) return '';
            if (typeof AJS !== 'undefined' && AJS.escapeHtml) {
                return AJS.escapeHtml(str);
            }
            var div = document.createElement('div');
            div.appendChild(document.createTextNode(str));
            return div.innerHTML;
        }

        // ─────────────── Init ───────────────

        loadConfig();
        loadGlossary();
        loadForbiddenWords();
    });

})(typeof AJS !== 'undefined' && AJS.$ ? AJS.$ : jQuery);
