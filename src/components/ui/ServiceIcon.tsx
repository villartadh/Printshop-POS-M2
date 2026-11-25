import React from 'react';
import {
  BookOpen,
  Camera,
  Contact,
  Copy,
  FileText,
  Image,
  Layers,
  Palette,
  Printer,
  Ribbon,
  ScanLine,
  Scissors,
  Sparkles,
  Tag,
} from 'lucide-react';

export const AVAILABLE_SERVICE_ICONS = [
  'FileText',
  'Palette',
  'Copy',
  'Contact',
  'Layers',
  'BookOpen',
  'Ribbon',
  'Image',
  'Camera',
  'Printer',
  'ScanLine',
  'Scissors',
  'Tag',
] as const;

export const ServiceIcon: React.FC<{ name?: string; className?: string }> = ({
  name = 'Printer',
  className = 'w-5 h-5',
}) => {
  switch (name) {
    case 'FileText':
      return <FileText className={className} />;
    case 'Palette':
      return <Palette className={className} />;
    case 'Copy':
      return <Copy className={className} />;
    case 'Contact':
      return <Contact className={className} />;
    case 'Layers':
      return <Layers className={className} />;
    case 'BookOpen':
      return <BookOpen className={className} />;
    case 'Ribbon':
      return <Ribbon className={className} />;
    case 'Image':
      return <Image className={className} />;
    case 'Camera':
      return <Camera className={className} />;
    case 'ScanLine':
      return <ScanLine className={className} />;
    case 'Scissors':
      return <Scissors className={className} />;
    case 'Tag':
      return <Tag className={className} />;
    case 'Sparkles':
      return <Sparkles className={className} />;
    default:
      return <Printer className={className} />;
  }
};
