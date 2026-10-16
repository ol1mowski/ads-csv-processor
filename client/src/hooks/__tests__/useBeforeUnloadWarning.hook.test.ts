import { renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useBeforeUnloadWarning } from '../useBeforeUnloadWarning.hook';

function fireBeforeUnload(): boolean {
  const event = new Event('beforeunload', { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

describe('useBeforeUnloadWarning', () => {
  it('asks before leaving only while active', () => {
    let active = false;
    const { rerender, unmount } = renderHook(() => {
      useBeforeUnloadWarning(active);
    });
    expect(fireBeforeUnload()).toBe(false);

    active = true;
    rerender();
    expect(fireBeforeUnload()).toBe(true);

    active = false;
    rerender();
    expect(fireBeforeUnload()).toBe(false);
    unmount();
  });

  it('stops asking once unmounted', () => {
    const { unmount } = renderHook(() => {
      useBeforeUnloadWarning(true);
    });

    unmount();

    expect(fireBeforeUnload()).toBe(false);
  });
});
