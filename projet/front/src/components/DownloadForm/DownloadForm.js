import React, { useState } from 'react';
import { startDownload } from '../../services/downloadService';
import './DownloadForm.css';

function DownloadForm({ onNewDownload }) {
    const [url, setUrl] = useState('');
    const [fileName, setFileName] = useState('');
    const [threads, setThreads] = useState(4);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!url) {
            setError('URL is required');
            return;
        }

        try {
            setIsLoading(true);
            const downloadData = {
                url,
                fileName: fileName || undefined,
                numberOfThreads: threads
            };

            const newDownload = await startDownload(downloadData);
            onNewDownload(newDownload);

            setUrl('');
            setFileName('');
            setThreads(4);
        } catch (err) {
            setError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="download-form-container">
            <h2>Add New Download</h2>
            {error && <div className="error-message">{error}</div>}
            <form onSubmit={handleSubmit}>
                <div className="form-group">
                    <label htmlFor="url">File URL:</label>
                    <input
                        type="url"
                        id="url"
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        placeholder="https://example.com/file.zip"
                        required
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="fileName">File Name (optional):</label>
                    <input
                        type="text"
                        id="fileName"
                        value={fileName}
                        onChange={(e) => setFileName(e.target.value)}
                        placeholder="custom-name.ext"
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="threads">Number of Threads:</label>
                    <select
                        id="threads"
                        value={threads}
                        onChange={(e) => setThreads(parseInt(e.target.value))}
                    >
                        {[1, 2, 4, 8, 16].map(num => (
                            <option key={num} value={num}>{num}</option>
                        ))}
                    </select>
                </div>
                <button type="submit" disabled={isLoading}>
                    {isLoading ? 'Starting...' : 'Start Download'}
                </button>
            </form>
        </div>
    );
}

export default DownloadForm;