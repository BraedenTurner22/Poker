import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
    // Angular 21 defaults to zoneless change detection; the components here
    // (and Phaser/STOMP callbacks) still rely on zone.js to trigger it.
    providers: [provideZoneChangeDetection(), provideRouter(routes), provideHttpClient()]
};
