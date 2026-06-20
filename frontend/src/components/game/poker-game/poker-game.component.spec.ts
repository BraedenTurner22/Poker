import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PokerGameComponent } from './poker-game.component';

describe('PokerGameComponent', () => {
  let component: PokerGameComponent;
  let fixture: ComponentFixture<PokerGameComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PokerGameComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(PokerGameComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
