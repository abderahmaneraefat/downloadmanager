import React from 'react';
import './StatsPanel.css';

function StatsPanel({ stats }) {
    const formatSpeed = (bytesPerSecond) => {
        if (bytesPerSecond === 0) return '0 B/s';
        const k = 1024;
        const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s', 'TB/s'];
        const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k));
        return parseFloat((bytesPerSecond / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    return (
        <div className="stats-panel">
            <div className="stat-item">
                <div className="stat-value">{stats.active}</div>
                <div className="stat-label">Active</div>
            </div>
            <div className="stat-item">
                <div className="stat-value">{stats.completed}</div>
                <div className="stat-label">Completed</div>
            </div>
            <div className="stat-item">
                <div className="stat-value">{stats.failed}</div>
                <div className="stat-label">Failed</div>
            </div>
            <div className="stat-item">
                <div className="stat-value">{formatSpeed(stats.totalSpeed)}</div>
                <div className="stat-label">Total Speed</div>
            </div>
        </div>
    );
}

export default StatsPanel;