import React, { createContext, useContext, useState, useLayoutEffect, useRef } from 'react';

export const HeaderContext = createContext();

export function HeaderProvider({ children }) {
  const [headerOverride, setHeaderOverride] = useState(null);

  return (
    <HeaderContext.Provider value={{ headerOverride, setHeaderOverride }}>
      {children}
    </HeaderContext.Provider>
  );
}

export function useHeaderOverride(title, subtitle, onBack) {
  const { setHeaderOverride } = useContext(HeaderContext);

  // Giữ onBack mới nhất trong ref mà không trigger effect
  const onBackRef = useRef(onBack);
  onBackRef.current = onBack;

  useLayoutEffect(() => {
    setHeaderOverride({ title, subtitle, onBack: (...args) => onBackRef.current?.(...args) });
    return () => setHeaderOverride(null);
  }, [title, subtitle, setHeaderOverride]);
}