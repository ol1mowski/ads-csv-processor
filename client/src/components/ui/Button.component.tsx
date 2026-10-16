import type { ComponentProps } from 'react';
import { buttonClass, type ButtonVariant } from './buttonClass.util';

type ButtonProps = Omit<ComponentProps<'button'>, 'className'> & {
  readonly variant?: ButtonVariant;
};

export function Button({ variant = 'primary', type = 'button', ...props }: ButtonProps) {
  return <button type={type} className={buttonClass(variant)} {...props} />;
}
