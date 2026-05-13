import React, { createContext, useContext, useState, useLayoutEffect } from 'react';

export const HeaderContext = createContext();

export function HeaderProvider({ children }) {
  // Thay vì headerConfig, mình gọi nó là headerOverride
  const [headerOverride, setHeaderOverride] = useState(null);

  return (
    <HeaderContext.Provider value={{ headerOverride, setHeaderOverride }}>
      {children}
    </HeaderContext.Provider>
  );
}

// 🎙️ ĐỔI TÊN HOOK THÀNH useHeaderOverride
export function useHeaderOverride(title, subtitle, onBack) {
  const { setHeaderOverride } = useContext(HeaderContext);

  useLayoutEffect(() => {
    // Nếu có truyền vào title/onBack thì ghi đè
    setHeaderOverride({ title, subtitle, onBack });

    // Khi rời khỏi trang thì dọn dẹp (trả Navbar về mặc định)
    return () => setHeaderOverride(null);
    
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, subtitle, setHeaderOverride]); 
}