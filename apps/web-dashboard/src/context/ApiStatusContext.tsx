import React, { createContext, useContext, useState, useEffect } from 'react';
import {
  subscribeToConnectionStatus,
  isForceOffline,
  setForceOffline,
} from '../api/client';

interface ApiStatusContextType {
  isBackendReachable: boolean;
  isOfflineMode: boolean;
  toggleOfflineMode: () => void;
}

const ApiStatusContext = createContext<ApiStatusContextType | undefined>(undefined);

export const ApiStatusProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [reachable, setReachable] = useState(true);
  const [offlineForced, setOfflineForced] = useState(isForceOffline());

  useEffect(() => {
    const unsubscribe = subscribeToConnectionStatus((status) => {
      setReachable(status);
    });
    return () => unsubscribe();
  }, []);

  const toggleOfflineMode = () => {
    const nextVal = !offlineForced;
    setOfflineForced(nextVal);
    setForceOffline(nextVal);
  };

  const isOffline = offlineForced || !reachable;

  return (
    <ApiStatusContext.Provider
      value={{
        isBackendReachable: reachable,
        isOfflineMode: isOffline,
        toggleOfflineMode,
      }}
    >
      {children}
    </ApiStatusContext.Provider>
  );
};

export const useApiStatus = () => {
  const ctx = useContext(ApiStatusContext);
  if (!ctx) {
    throw new Error('useApiStatus must be used within an ApiStatusProvider');
  }
  return ctx;
};
