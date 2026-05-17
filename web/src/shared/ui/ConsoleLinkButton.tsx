import type { MouseEventHandler, ReactNode } from "react";
import { Link, type To } from "react-router-dom";

type ConsoleLinkButtonVariant = "primary" | "default";
type ConsoleLinkButtonSize = "small" | "middle";

export interface ConsoleLinkButtonProps {
	to: To;
	children: ReactNode;
	variant?: ConsoleLinkButtonVariant;
	size?: ConsoleLinkButtonSize;
	disabled?: boolean;
	onClick?: MouseEventHandler<HTMLAnchorElement>;
	className?: string;
	testId?: string;
}

function buildClassName({
	variant,
	size,
	className,
	disabled,
}: {
	variant: ConsoleLinkButtonVariant;
	size: ConsoleLinkButtonSize;
	className?: string;
	disabled?: boolean;
}) {
	return [
		"console-link-button",
		`console-link-button--${variant}`,
		`console-link-button--${size}`,
		disabled ? "console-link-button--disabled" : "",
		className ?? "",
	]
		.filter(Boolean)
		.join(" ");
}

export function ConsoleLinkButton({
	to,
	children,
	variant = "default",
	size = "middle",
	disabled = false,
	onClick,
	className,
	testId,
}: ConsoleLinkButtonProps) {
	const resolvedClassName = buildClassName({
		variant,
		size,
		className,
		disabled,
	});

	if (disabled) {
		return (
			<span
				className={resolvedClassName}
				aria-disabled="true"
				data-testid={testId}
			>
				{children}
			</span>
		);
	}

	return (
		<Link
			to={to}
			className={resolvedClassName}
			onClick={onClick}
			data-testid={testId}
		>
			{children}
		</Link>
	);
}
