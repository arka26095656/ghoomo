import { useEffect, type RefObject } from 'react';

/**
 * Closes a popover (menu, dropdown) when the user clicks or taps outside it, or presses Escape.
 * Pass every element that counts as "inside": usually the popover and the button that opens it,
 * so clicking the button again toggles instead of closing and instantly reopening.
 */
export function useDismiss(open: boolean, onClose: () => void, refs: RefObject<HTMLElement | null>[]) {
  useEffect(() => {
    if (!open) return;

    const onPointer = (e: PointerEvent) => {
      const target = e.target as Node | null;
      if (target && refs.some((r) => r.current?.contains(target))) return;
      onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };

    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
    // refs are stable objects; listing them would re-subscribe on every render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, onClose]);
}
