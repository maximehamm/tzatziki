// This file doesn't have an intention to express equivalents of Flow utility types using TypeScript types,
// it just helps an IDE to provide similar experience with basic highlighting and completion, but without full type checking.
//
// https://flow.org/en/docs/types/utilities/

type $Keys<T> = keyof T;

type $Values<T> = any;

type $ReadOnly<T> = Readonly<T>;

type $Exact<T> = T; // object types are exact in typescript, but not in flow

type $Diff<A, B> = { [K in Exclude<keyof A, keyof B>]: A[K]};

type $Rest<A, B> = $Diff<A, B>;

type $PropertyType<T, k extends keyof T> = T[k];

type $ElementType<T, K extends keyof T> = T[K];

type $NonMaybeType<T> = NonNullable<T>;

type $ObjMap<T, F> = any;

type $TupleMap<T, F> = any;

type $Call<F extends (...args: any[]) => any> = ReturnType<F>;

type Class<T> = { new(): T };

type $Shape<T> = Partial<T>;

type $Supertype<T> = any;

type $Subtype<T> = any;

// not documented
type $Exports<T> = any;
