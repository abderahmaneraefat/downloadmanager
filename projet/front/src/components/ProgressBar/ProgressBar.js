import React from 'react';
import './ProgressBar.css';

function ProgressBar({ progress, status }) {
    const getProgressColor = () => {
        switch (status) {
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
        <div className="progress-bar-container">
            <div
                className="progress-bar"
                style={{
                    width: `${progress}%`,
                    backgroundColor: getProgressColor()
                }}
            ></div>
            <div className="progress-text">{progress.toFixed(1)}%</div>
        </div>
    );
}

export default ProgressBar;