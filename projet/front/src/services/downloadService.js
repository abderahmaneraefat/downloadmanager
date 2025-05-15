import axios from 'axios';

const API_URL = 'http://localhost:8086/api/downloads';

export const startDownload = async (downloadData) => {
    try {
        const response = await axios.post(API_URL, downloadData);
        return response.data;
    } catch (error) {
        throw new Error(error.response?.data || 'Failed to start download');
    }
};

export const getDownloads = async () => {
    try {
        const response = await axios.get(API_URL);
        return response.data;
    } catch (error) {
        throw new Error('Failed to fetch downloads');
    }
};

export const getDownloadProgress = async (id) => {
    try {
        const response = await axios.get(`${API_URL}/${id}`);
        return response.data;
    } catch (error) {
        throw new Error('Failed to fetch download progress');
    }
};

export const pauseDownload = async (id) => {
    try {
        await axios.post(`${API_URL}/${id}/pause`);
    } catch (error) {
        throw new Error('Failed to pause download');
    }
};

export const resumeDownload = async (id) => {
    try {
        await axios.post(`${API_URL}/${id}/resume`);
    } catch (error) {
        throw new Error('Failed to resume download');
    }
};

export const cancelDownload = async (id) => {
    try {
        await axios.post(`${API_URL}/${id}/cancel`);
    } catch (error) {
        throw new Error('Failed to cancel download');
    }
};

export const deleteDownload = async (id) => {
    try {
        await axios.delete(`${API_URL}/${id}`);
    } catch (error) {
        throw new Error('Failed to delete download');
    }
};

// Polling function to update download progress
export const pollDownloadProgress = async (id, interval = 1000) => {
    return new Promise((resolve) => {
        const intervalId = setInterval(async () => {
            try {
                const progress = await getDownloadProgress(id);
                if (progress.status === 'COMPLETED' || progress.status === 'FAILED' || progress.status === 'CANCELLED') {
                    clearInterval(intervalId);
                    resolve(progress);
                }
            } catch (error) {
                clearInterval(intervalId);
                resolve(null);
            }
        }, interval);
    });
};