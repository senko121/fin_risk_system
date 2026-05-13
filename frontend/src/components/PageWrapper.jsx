import React from 'react';
import { motion } from 'framer-motion';

const variants = {
  initial: { opacity: 0 },
  in:      { opacity: 1, transition: { duration: 0.2, ease: 'easeInOut' } },
  out:     { opacity: 0, transition: { duration: 0.2, ease: 'easeInOut' } },
};

export default function PageWrapper({ children }) {
  return (
    <motion.div
      initial="initial"
      animate="in"
      exit="out"
      variants={variants}
      style={{ willChange: 'opacity' }}
      // THAY ĐỔI Ở ĐÂY: Dùng absolute để trang mới đè lên trang cũ, chống giật Layout
      className="absolute top-0 left-0 w-full min-h-screen" 
    >
      {children}
    </motion.div>
  );
}