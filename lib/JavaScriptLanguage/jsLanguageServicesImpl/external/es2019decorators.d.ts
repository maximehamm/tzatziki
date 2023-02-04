declare decorator @wrap(wrapper: (obj: any) => any);
declare decorator @register(callback: (target, propertyKey?) => undefined);
declare decorator @initialize(callback: (obj: any, fieldName: string, value: any) => undefined);
declare decorator @expose(callback: (target, propertyKey?) => undefined);