import fr from './fr';
import ar from './ar';

export const languages = {
  fr: { label: 'FR', dir: 'ltr' },
  ar: { label: 'AR', dir: 'rtl' }
};

export const translations = { fr, ar };

export function getDirection(language) {
  return languages[language]?.dir || 'ltr';
}
