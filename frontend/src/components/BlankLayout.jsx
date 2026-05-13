import React from 'react';
import { Outlet } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';

export default function BlankLayout() {
  return (
    <div className="flex-1 w-full h-full relative overflow-y-auto hide-scrollbar">
      <AnimatePresence mode="wait">
        <Outlet />
      </AnimatePresence>
    </div>
  );
}