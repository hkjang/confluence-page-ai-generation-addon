/**
 * AI Generation Polling - Handles job status polling and progress display
 * Shows progress dialog, polls every 2s, handles cancel/complete transitions
 */
(function($) {
    'use strict';

    var AJS = window.AJS || {};
    var REST_BASE = AJS.contextPath() + '/rest/ai-generation/1.0';
    var POLL_INTERVAL = 2000;
    var pollTimer = null;
    var currentJobId = null;
    var currentTemplateKey = null;
    var currentSpaceKey = null;

    /**
     * Start polling a generation job
     */
    function startPolling(jobId, templateKey, spaceKey) {
        currentJobId = jobId;
        currentTemplateKey = templateKey;
        currentSpaceKey = spaceKey;

        showProgressDialog(jobId);
        poll();
    }

    /**
     * Show progress dialog
     */
    function showProgressDialog(jobId) {
        var progressHtml = AiGeneration.Components.generationProgress({jobId: jobId});

        // Wrap in a dialog
        var dialogHtml = '<section id="ai-generation-progress-dialog" class="aui-dialog2 aui-dialog2-medium aui-layer" ' +
            'role="dialog" aria-hidden="true" data-aui-remove-on-hide="true">' +
            '<header class="aui-dialog2-header">' +
            '<h2 class="aui-dialog2-header-main">' + AJS.I18n.getText('ai.generation.progress.title') + '</h2>' +
            '</header>' +
            '<div class="aui-dialog2-content">' + progressHtml + '</div>' +
            '</section>';

        $(dialogHtml).appendTo('body');
        var $dialog = $('#ai-generation-progress-dialog');
        AJS.dialog2($dialog).show();

        // Bind cancel button
        $dialog.on('click', '#ai-gen-cancel-generation', function() {
            cancelGeneration();
        });
    }

    /**
     * Poll job status
     */
    function poll() {
        if (!currentJobId) return;

        $.ajax({
            url: REST_BASE + '/generation/progress/' + currentJobId,
            dataType: 'json',
            success: function(data) {
                updateProgress(data);

                if (isTerminalStatus(data.status)) {
                    stopPolling();
                    onJobComplete(data);
                } else {
                    pollTimer = setTimeout(poll, POLL_INTERVAL);
                }
            },
            error: function(xhr) {
                if (xhr.status === 404) {
                    stopPolling();
                    showError(AJS.I18n.getText('ai.generation.progress.job.not.found'));
                } else {
                    // Retry on transient errors
                    pollTimer = setTimeout(poll, POLL_INTERVAL * 2);
                }
            }
        });
    }

    /**
     * Update progress UI
     */
    function updateProgress(data) {
        var percent = data.percentComplete || 0;
        var statusText = getStatusText(data.status);

        // Update progress bar
        $('#ai-gen-progress-bar-container').html(
            AiGeneration.Components.progressBar({
                percent: percent,
                label: statusText
            })
        );

        // Update status lozenge
        $('#ai-gen-progress-status').text(statusText)
            .removeClass('aui-lozenge-success aui-lozenge-error aui-lozenge-current')
            .addClass(getStatusLozengeClass(data.status));
    }

    /**
     * Handle job completion
     */
    function onJobComplete(data) {
        var status = data.status;

        if (status === 'COMPLETED' || status === 'PARTIAL') {
            // Update progress to 100%
            updateProgress({percentComplete: 100, status: status});

            // Close progress dialog after brief delay
            setTimeout(function() {
                closeProgressDialog();
                // Open preview
                AiGeneration.Preview.openPreview(currentJobId, currentTemplateKey, currentSpaceKey);
            }, 1000);

        } else if (status === 'FAILED') {
            updateProgress({percentComplete: data.percentComplete || 0, status: 'FAILED'});
            $('#ai-gen-progress-bar-container').append(
                AiGeneration.Components.errorPanel({
                    message: AJS.I18n.getText('ai.generation.progress.failed')
                })
            );
            // Add retry button
            $('#ai-gen-cancel-generation').text(AJS.I18n.getText('ai.generation.progress.retry'))
                .off('click').on('click', function() {
                    retryGeneration();
                });

        } else if (status === 'CANCELLED') {
            updateProgress({percentComplete: data.percentComplete || 0, status: 'CANCELLED'});
            setTimeout(closeProgressDialog, 1500);
        }
    }

    /**
     * Cancel current generation
     */
    function cancelGeneration() {
        if (!currentJobId) return;

        $.ajax({
            url: REST_BASE + '/generation/cancel/' + currentJobId,
            type: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            success: function() {
                AJS.flag({type: 'info', title: AJS.I18n.getText('ai.generation.progress.cancelled'), close: 'auto'});
            },
            error: function() {
                AJS.flag({type: 'error', title: AJS.I18n.getText('ai.generation.progress.cancel.error'), close: 'auto'});
            }
        });
    }

    /**
     * Retry failed generation
     */
    function retryGeneration() {
        if (!currentJobId) return;

        $.ajax({
            url: REST_BASE + '/generation/retry/' + currentJobId,
            type: 'POST',
            contentType: 'application/json',
            dataType: 'json',
            success: function(data) {
                closeProgressDialog();
                startPolling(data.jobId, currentTemplateKey, currentSpaceKey);
            },
            error: function(xhr) {
                var errMsg = 'Retry failed';
                try { errMsg = JSON.parse(xhr.responseText).error; } catch(e) {}
                AJS.flag({type: 'error', title: errMsg, close: 'auto'});
            }
        });
    }

    /**
     * Stop polling timer
     */
    function stopPolling() {
        if (pollTimer) {
            clearTimeout(pollTimer);
            pollTimer = null;
        }
    }

    /**
     * Close progress dialog
     */
    function closeProgressDialog() {
        var $dialog = $('#ai-generation-progress-dialog');
        if ($dialog.length) {
            AJS.dialog2($dialog).hide();
            $dialog.remove();
        }
    }

    function showError(msg) {
        $('#ai-gen-progress-bar-container').html(
            AiGeneration.Components.errorPanel({message: msg})
        );
    }

    // ─────────────── Helpers ───────────────

    function isTerminalStatus(status) {
        return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED' || status === 'PARTIAL';
    }

    function getStatusText(status) {
        var key = 'ai.generation.progress.status.' + status.toLowerCase();
        return AJS.I18n.getText(key) || status;
    }

    function getStatusLozengeClass(status) {
        switch (status) {
            case 'COMPLETED': return 'aui-lozenge-success';
            case 'PARTIAL': return 'aui-lozenge-current';
            case 'FAILED': return 'aui-lozenge-error';
            case 'CANCELLED': return 'aui-lozenge-moved';
            case 'IN_PROGRESS': return 'aui-lozenge-current';
            default: return '';
        }
    }

    // Expose namespace
    window.AiGeneration = window.AiGeneration || {};
    window.AiGeneration.Polling = {
        startPolling: startPolling,
        stopPolling: stopPolling
    };

})(AJS.$);
