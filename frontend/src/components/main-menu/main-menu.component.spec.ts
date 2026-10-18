import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../services/auth.service';

import { MainMenuComponent } from './main-menu.component';

describe('MainMenuComponent', () => {
  let component: MainMenuComponent;
  let fixture: ComponentFixture<MainMenuComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MainMenuComponent],
      providers: [provideHttpClient(), provideRouter([])],
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(MainMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('retries a failed profile load when the profile button is clicked', () => {
    const auth = TestBed.inject(AuthService);
    const profile = { id: 1, name: 'A', email: 'a@x.edu', collegeName: 'X', collegeDomain: 'x.edu', chips: 5, totalChipDifference: 0 };
    const spy = spyOn(auth, 'getProfile').and.returnValues(
      throwError(() => ({ status: 502 })), of(profile));
    component.isLoggedIn = true;
    (component as any).loadProfile(false);   // first load fails transiently
    expect(component.profile).toBeNull();
    expect(component.isLoggedIn).toBeTrue();

    component.openProfile();
    expect(spy).toHaveBeenCalledTimes(2);
    expect(component.profile).toEqual(profile);
    expect(component.showProfile).toBeTrue();
  });
});
