/*
 * Copyright 2014 lprimak.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.flowlogix.jeedao.primefaces.internal;

import com.flowlogix.jeedao.DaoHelper;
import com.flowlogix.jeedao.primefaces.JPALazyDataModel;
import com.flowlogix.jeedao.primefaces.interfaces.EntityManagerGetter;
import com.flowlogix.jeedao.primefaces.interfaces.Filter;
import com.flowlogix.jeedao.primefaces.interfaces.Optimizer;
import com.flowlogix.jeedao.primefaces.interfaces.Sorter;
import com.flowlogix.jeedao.primefaces.support.FilterData;
import com.flowlogix.jeedao.primefaces.support.SortData;
import com.flowlogix.util.TypeConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;

/**
 * JPA DAO facade implementation for the PrimeFaces lazy table model
 * @author lprimak
 * @param <TT>
 * @param <KK>
 */
@Stateless @Slf4j
public class JPAFacade<TT, KK> extends DaoHelper<TT, KK> implements JPAFacadeLocal<TT, KK>
{
    public JPAFacade(JPAFacadeState state, Class<TT> entityClass)
    {
        super(entityClass);
        this.state = state;
    }


    @Override
    public void setup(EntityManagerGetter emg, Class<TT> entityClass, Optimizer<TT> optimizer,
            Filter<TT> filter, Sorter<TT> sorter)
    {
        getState().setEmg(emg);
        getState().setEntityClass(entityClass);
        getState().setOptimizer(optimizer);
        getState().setFilterHook(filter);
        getState().setSorterHook(sorter);
    }


    @Override
    protected EntityManager getEntityManager()
    {
        return getState().getEmg().get();
    }


    @Override
    public Class<TT> getEntityClass()
    {
        return getState().getEntityClass();
    }


    @Override
    public int count(Map<String, FilterMeta> filters)
    {
        getState().setFilters(filters);
        getState().setSortMeta(new HashMap<>());
        return super.count();
    }


    @Override
    public List<TT> findRows(int first, int pageSize, Map<String, FilterMeta> filters, Map<String, SortMeta> sortMeta)
    {
        getState().setFilters(filters);
        getState().setSortMeta(sortMeta);
        return super.findRange(new int[] { first, first + pageSize });
    }


    @Override
    protected void addToCriteria(CriteriaBuilder cb, Root<TT> root, CriteriaQuery<TT> cq)
    {
        cq.where(getFilters(getState().getFilters(), cb, root));
        cq.orderBy(getSort(getState().getSortMeta(), cb, root));
        root.alias(JPALazyDataModel.RESULT);
    }


    @Override
    protected void addToCountCriteria(CriteriaBuilder cb, Root<TT> root, CriteriaQuery<Long> cq)
    {
        cq.where(getFilters(getState().getFilters(), cb, root));
    }


    @Override
    protected void addHints(TypedQuery<TT> tq, boolean isRange)
    {
        if(getState().getOptimizer() != null)
        {
            getState().getOptimizer().addHints(tq);
        }
    }


    private Predicate getFilters(Map<String, FilterMeta> filters, CriteriaBuilder cb, Root<TT> root)
    {
        Map<String, FilterData> predicates = new HashMap<>();
        filters.forEach((key, value) ->
        {
            Predicate cond = null;
            try
            {
                Class<?> fieldType = root.get(key).getJavaType();
                if (fieldType == String.class)
                {
                    cond = cb.like(root.get(key), String.format("%%%s%%", value));
                } else
                {
                    if (TypeConverter.checkType(value.toString(), fieldType))
                    {
                        cond = cb.equal(root.get(key), value);
                    }
                }
            }
            catch(IllegalArgumentException e) { /* ignore possibly extra filter fields */}
            predicates.put(key, new FilterData(value.toString(), cond));
        });
        if(getState().getFilterHook() != null)
        {
            getState().getFilterHook().filter(predicates, cb, root);
        }
        return cb.and(predicates.values().stream().map(FilterData::getPredicate)
                .filter(Objects::nonNull).toArray(Predicate[]::new));
    }


    private List<Order> getSort(Map<String, SortMeta> sortCriteria, CriteriaBuilder cb, Root<TT> root)
    {
        SortData sortData = new SortData(sortCriteria);
        if(getState().getSorterHook() != null)
        {
            getState().getSorterHook().sort(sortData, cb, root);
        }

        List<Order> sortMetaOrdering = processSortMeta(sortData.getSortMeta(), cb, root);
        List<Order> rv = new ArrayList<>();
        if(sortData.isAppendSortOrder())
        {
            rv.addAll(sortMetaOrdering);
            rv.addAll(sortData.getSortOrder());
        }
        else
        {
            rv.addAll(sortData.getSortOrder());
            rv.addAll(sortMetaOrdering);
        }
        return rv;
    }


    private List<Order> processSortMeta(Map<String, SortMeta> sortMeta, CriteriaBuilder cb, Root<TT> root)
    {
        List<Order> sortMetaOrdering = new ArrayList<>();
        sortMeta.forEach((field, order) ->
        {
            switch(order.getSortOrder())
            {
                case ASCENDING:
                    sortMetaOrdering.add(cb.asc(root.get(order.getSortField())));
                    break;
                case DESCENDING:
                    sortMetaOrdering.add(cb.desc(root.get(order.getSortField())));
                    break;
            }
        });
        return sortMetaOrdering;
    }


    @SuppressWarnings("unchecked")
    private JPAFacadeTypedState<TT> getState()
    {
        return (JPAFacadeTypedState<TT>) state.getTypedState();
    }


    private @Inject JPAFacadeState state;
}
