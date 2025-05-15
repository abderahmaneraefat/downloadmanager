import React, { useState } from 'react';
import ProgressBar from '../ProgressBar/ProgressBar';
import {
    pauseDownload,
    resumeDownload,
    cancelDownload,
    deleteDownload
} from '../../services/downloadService';
import './DownloadItem.css';

function DownloadItem({ download, onUpdate, onRemove }) {
    const [isBusy, setIsBusy] = useState(false);

    const handlePause = async () => {
        try {
            setIsBusy(true);
            await pauseDownload(download.id);
            onUpdate(download.id, { status: 'PAUSED' });
        } catch (error) {
            console.error('Failed to pause download:', error);
        } finally {
            setIsBusy(false);
        }
    };

    const handleResume = async () => {
        try {
            setIsBusy(true);
            await resumeDownload(download.id);
            onUpdate(download.id, { status: 'QUEUED' });
        } catch (error) {
            console.error('Failed to resume download:', error);
        } finally {
            setIsBusy(false);
        }
    };

    const handleCancel = async () => {
        try {
            setIsBusy(true);
            await cancelDownload(download.id);
            onUpdate(download.id, { status: 'CANCELLED' });
        } catch (error) {
            console.error('Failed to cancel download:', error);
        } finally {
            setIsBusy(false);
        }
    };

    const handleDelete = async () => {
        try {
            setIsBusy(true);
            await deleteDownload(download.id);
            onRemove(download.id);
        } catch (error) {
            console.error('Failed to delete download:', error);
        } finally {
            setIsBusy(false);
        }
    };

    const formatBytes = (bytes) => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    const formatSpeed = (bytesPerSecond) => {
        return formatBytes(bytesPerSecond) + '/s';
    };

    const getStatusColor = () => {
        switch (download.status) {
            case 'DOWNLOADING':
                return '#2ecc71';
            case 'PAUSED':
                return '#f39c12';
            case 'COMPLETED':
                return '#27ae60';
            case 'FAILED':
            case 'CANCELLED':
                return '#e74c3c';
            case 'QUEUED':
                return '#3498db';
            default:
                return '#95a5a6';
        }
    };

    return (
        <div className="download-item" style={{ borderLeft: `4px solid ${getStatusColor()}` }}>
            <div className="download-info">
                <h3 className="download-name">{download.fileName}</h3>
                <div className="download-url">{download.url}</div>
                <div className="download-meta">
          <span className="download-status" style={{ color: getStatusColor() }}>
            {download.status}
          </span>
                    <span className="download-size">
            {formatBytes(download.downloadedBytes)} of {formatBytes(download.fileSize)}
          </span>
                    {download.downloadSpeed > 0 && (
                        <span className="download-speed">
              {formatSpeed(download.downloadSpeed)}
            </span>
                    )}
                    <span className="download-threads">
            {download.numberOfThreads} thread{download.numberOfThreads !== 1 ? 's' : ''}
          </span>
                </div>
                <ProgressBar
                    progress={download.progress || 0}
                    status={download.status}
                />
            </div>
            <div className="download-actions">
                {download.status === 'DOWNLOADING' && (
                    <button
                        onClick={handlePause}
                        disabled={isBusy}
                        className="action-button pause"
                    >
                        Pause
                    </button>
                )}
                {download.status === 'PAUSED' && (
                    <button
                        onClick={handleResume}
                        disabled={isBusy}
                        className="action-button resume"
                    >
                        Resume
                    </button>
                )}
                {(download.status === 'DOWNLOADING' || download.status === 'PAUSED' || download.status === 'QUEUED') && (
                    <button
                        onClick={handleCancel}
                        disabled={isBusy}
                        className="action-button cancel"
                    >
                        Cancel
                    </button>
                )}
                <button
                    onClick={handleDelete}
                    disabled={isBusy}
                    className="action-button delete"
                >
                    Delete
                </button>
            </div>
        </div>
    );
}

export default DownloadItem;