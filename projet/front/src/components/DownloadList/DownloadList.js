import React, { useEffect, useState } from 'react';
import DownloadItem from '../DownloadItem/DownloadItem';
import { getDownloads, pollDownloadProgress } from '../../services/downloadService';
import './DownloadList.css';

function DownloadList({ downloads, onUpdateDownload, onRemoveDownload }) {
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const fetchDownloads = async () => {
            try {
                const initialDownloads = await getDownloads();
                initialDownloads.forEach(download => {
                    if (!downloads.some(d => d.id === download.id)) {
                        onUpdateDownload(download.id, download);
                    }
                });
            } catch (error) {
                console.error('Failed to fetch downloads:', error);
            } finally {
                setIsLoading(false);
            }
        };

        fetchDownloads();
    }, []);

    useEffect(() => {
        const activeDownloads = downloads.filter(
            d => d.status === 'DOWNLOADING' || d.status === 'QUEUED'
        );

        activeDownloads.forEach(download => {
            pollDownloadProgress(download.id).then(updatedDownload => {
                if (updatedDownload) {
                    onUpdateDownload(download.id, updatedDownload);
                }
            });
        });
    }, [downloads]);

    const handleRemove = (id) => {
        onRemoveDownload(id);
    };

    if (isLoading) {
        return <div className="loading">Loading downloads...</div>;
    }

    if (downloads.length === 0) {
        return <div className="no-downloads">No downloads yet. Add a new download to get started!</div>;
    }

    return (
        <div className="download-list-container">
            <h2>Downloads</h2>
            <div className="download-list">
                {downloads.map(download => (
                    <DownloadItem
                        key={download.id}
                        download={download}
                        onUpdate={onUpdateDownload}
                        onRemove={handleRemove}
                    />
                ))}
            </div>
        </div>
    );
}

export default DownloadList;