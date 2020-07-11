; Test case:

	ld ix,list
	ld a,(ix+1)	; this should be optimized to use hl
	ld (var),a

	ld ix,list
	ld a,(ix)	; this should be optimized to use hl too
	ld (var),a

	ld hl,0
	ld ix,list
	ld a,(ix)	; this should not be optimized, as we would modify hl
	ld (var),a
	ld a,(hl)
	ld (var),a

end:
	jp end

var:
	db 0

list:
	db 1,2,3,4