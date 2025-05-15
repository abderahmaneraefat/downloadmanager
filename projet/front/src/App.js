import React, { useState, useEffect } from 'react';
import DownloadForm from './components/DownloadForm/DownloadForm';
import DownloadList from './components/DownloadList/DownloadList';
import StatsPanel from './components/StatsPanel/StatsPanel';
import './App.css';

function App() {
  const [downloads, setDownloads] = useState([]);
  const [stats, setStats] = useState({
    active: 0,
    completed: 0,
    failed: 0,
    totalSpeed: 0
  });

  useEffect(() => {
    const interval = setInterval(() => {
      // Update stats based on current downloads
      const active = downloads.filter(d => d.status === 'DOWNLOADING' || d.status === 'QUEUED').length;
      const completed = downloads.filter(d => d.status === 'COMPLETED').length;
      const failed = downloads.filter(d => d.status === 'FAILED' || d.status === 'CANCELLED').length;
      const totalSpeed = downloads
          .filter(d => d.status === 'DOWNLOADING')
          .reduce((sum, d) => sum + (d.downloadSpeed || 0), 0);

      setStats({ active, completed, failed, totalSpeed });
    }, 1000);

    return () => clearInterval(interval);
  }, [downloads]);

  const handleNewDownload = (newDownload) => {
    setDownloads(prev => [...prev, newDownload]);
  };

  const updateDownload = (id, updatedData) => {
    setDownloads(prev =>
        prev.map(download =>
            download.id === id ? { ...download, ...updatedData } : download
        )
    );
  };

  const removeDownload = (id) => {
    setDownloads(prev => prev.filter(download => download.id !== id));
  };

  return (
      <div className="app-container">
        <header className="app-header">
          <h1>Advanced Download Manager</h1>
        </header>
        <StatsPanel stats={stats} />
        <DownloadForm onNewDownload={handleNewDownload} />
        <DownloadList
            downloads={downloads}
            onUpdateDownload={updateDownload}
            onRemoveDownload={removeDownload}
        />
      </div>
  );
}

export default App;