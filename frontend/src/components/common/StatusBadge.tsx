import React from 'react';
import type { ProcessingStatus, ConfirmationStatus, Priority, ExceptionCategory } from '../../types/tcem';

interface StatusBadgeProps {
  status?: ProcessingStatus | ConfirmationStatus | string;
  type?: 'status' | 'confirmation' | 'priority' | 'category' | 'role';
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, type = 'status', size = 'sm' }) => {
  if (!status) return null;

  const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm font-medium';

  // ProcessingStatus styling
  if (type === 'status') {
    switch (status) {
      case 'CONFIRMED':
      case 'STP_PASSED':
      case 'RESOLVED':
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5"></span>
            {status}
          </span>
        );
      case 'EXCEPTION_CAPTURED':
      case 'STP_FAILED':
      case 'SCHEMA_INVALID':
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-rose-400 mr-1.5"></span>
            {status}
          </span>
        );
      case 'MANUAL_REVIEW':
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 mr-1.5 animate-pulse"></span>
            MANUAL REVIEW
          </span>
        );
      case 'DEAD_LETTER':
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-red-600/15 text-red-400 border border-red-500/30 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-red-500 mr-1.5"></span>
            DEAD LETTER
          </span>
        );
      case 'WORKITEM_LINKED':
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-blue-500/10 text-blue-400 border border-blue-500/20 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-blue-400 mr-1.5"></span>
            WORK ITEM LINKED
          </span>
        );
      case 'REPLAYED':
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 mr-1.5"></span>
            REPLAYED
          </span>
        );
      case 'ENRICHED':
      case 'ACCEPTED':
      case 'VALIDATING':
      case 'ENRICHING':
      case 'RECEIVED':
      default:
        return (
          <span className={`inline-flex items-center rounded-full font-medium bg-slate-500/10 text-slate-300 border border-slate-500/20 ${sizeClasses}`}>
            <span className="w-1.5 h-1.5 rounded-full bg-slate-400 mr-1.5"></span>
            {status}
          </span>
        );
    }
  }

  // Priority styling
  if (type === 'priority') {
    switch (status as Priority) {
      case 'CRITICAL':
        return (
          <span className={`inline-flex items-center rounded font-semibold bg-red-500/15 text-red-400 border border-red-500/30 ${sizeClasses}`}>
            CRITICAL
          </span>
        );
      case 'HIGH':
        return (
          <span className={`inline-flex items-center rounded font-semibold bg-rose-500/15 text-rose-400 border border-rose-500/30 ${sizeClasses}`}>
            HIGH
          </span>
        );
      case 'MEDIUM':
        return (
          <span className={`inline-flex items-center rounded font-medium bg-amber-500/15 text-amber-400 border border-amber-500/30 ${sizeClasses}`}>
            MEDIUM
          </span>
        );
      case 'LOW':
      default:
        return (
          <span className={`inline-flex items-center rounded font-medium bg-slate-500/15 text-slate-400 border border-slate-500/20 ${sizeClasses}`}>
            LOW
          </span>
        );
    }
  }

  // Category styling
  if (type === 'category') {
    const cat = status as ExceptionCategory;
    switch (cat) {
      case 'MISSING_ECONOMIC_DATA':
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20 ${sizeClasses}`}>
            Missing Economic Data
          </span>
        );
      case 'MISSING_STATIC_REFERENCE_DATA':
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20 ${sizeClasses}`}>
            Missing Static Data
          </span>
        );
      case 'TEMPLATE_ISSUE':
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-purple-500/10 text-purple-400 border border-purple-500/20 ${sizeClasses}`}>
            Template Issue
          </span>
        );
      case 'BUSINESS_RULE_FAILURE':
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-blue-500/10 text-blue-400 border border-blue-500/20 ${sizeClasses}`}>
            Business Rule Failure
          </span>
        );
      case 'TECHNICAL_PROCESSING_FAILURE':
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-red-600/15 text-red-400 border border-red-500/30 ${sizeClasses}`}>
            Technical Failure
          </span>
        );
      case 'COUNTERPARTY_MATCHING_DISPUTE':
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-orange-500/10 text-orange-400 border border-orange-500/20 ${sizeClasses}`}>
            Matching Dispute
          </span>
        );
      default:
        return (
          <span className={`inline-flex items-center rounded-md font-medium bg-slate-500/10 text-slate-400 border border-slate-500/20 ${sizeClasses}`}>
            {status}
          </span>
        );
    }
  }

  return (
    <span className={`inline-flex items-center rounded-full font-medium bg-slate-500/10 text-slate-300 border border-slate-500/20 ${sizeClasses}`}>
      {status}
    </span>
  );
};
